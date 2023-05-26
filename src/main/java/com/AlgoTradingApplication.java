package com;

import com.angelbroking.smartapi.SmartConnect;
import com.angelbroking.smartapi.http.SessionExpiryHook;
import com.angelbroking.smartapi.models.User;
import com.google.gson.*;
import com.nifty.MorningService;
import com.nifty.dto.Candle;
import com.trading.Index;
import de.taimos.totp.TOTP;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com"}, exclude = {SecurityAutoConfiguration.class})
public class AlgoTradingApplication implements ApplicationRunner {

    @Autowired
    MorningService morningService;
    @Value("${morning.url}")
    private String url;

    //  mvn spring-boot:run -Dspring-boot.run.arguments=--morning.url=http://localhost:8090/tree

    public static void main(String[] args) throws Exception {
        SpringApplication.run(AlgoTradingApplication.class, args);
        List<Candle> candleList = new ArrayList<>();

        candleList.add(Candle.builder().high(1).build());
        candleList.add(Candle.builder().high(2).build());
        candleList.add(Candle.builder().high(3).build());

        candleList.add(Candle.builder().high(4).build());
        candleList.add(Candle.builder().high(5).build());
        candleList.add(Candle.builder().high(6).build());
        candleList.add(Candle.builder().high(7).build());
        candleList.add(Candle.builder().high(8).build());
        candleList.add(Candle.builder().high(9).build());
        candleList.add(Candle.builder().high(10).build());
        candleList.add(Candle.builder().high(11).build());
        candleList.add(Candle.builder().high(12).build());
        candleList.add(Candle.builder().high(13).build());

        List<Candle> ans = extractLastNCandles(candleList,10);

        System.out.println(ans.size());

    }

    public static List<Candle> extractLastNCandles(List<Candle> candleList,int n){
        List<Candle> finalCandles = new ArrayList<>();
        int size = candleList.size();

        if(n > size){
            log.error("N is greater  then candles List size");
            return new ArrayList<>();
        }

        for(int i = size-n;i<size;i++){
            finalCandles.add(candleList.get(i));
        }
        return finalCandles;
    }

    public static void execute(List<Candle> candleList) {
        SmartConnect smartConnect = connectWithAngel();

        /*
        every 10 seconds  start with count = 1...6 times in 1 min   ...... 30 times in 5 mins
         when count becomes 30  ...do this
             close_curr = ltp = open_next
            curr.high = high;
            curr.low = low
         */

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        final boolean[] isStarted = {false};

        AtomicReference<Double> open = new AtomicReference<>();

        if(!candleList.isEmpty() && !isStarted[0]){
            open.set(candleList.get(candleList.size()-1).getClose());
        }

        AtomicReference<Double> close = new AtomicReference<>();
        AtomicReference<Double> low = new AtomicReference(999999.23);
        AtomicReference<Double> high = new AtomicReference(-999.23);


        Runnable periodicRunnable = () -> {
            try {
                String ltp = getNiftyltp(smartConnect);

                double niftyLtp = Double.parseDouble(ltp);

                if (!isStarted[0]) {
                    open.set(niftyLtp);
                    isStarted[0] = true;
                } else {
                    high.set(Math.max(niftyLtp, high.get()));
                    low.set(Math.min(niftyLtp, low.get()));
                }

                if (System.currentTimeMillis() - startTime.get() >= 300000) {
                    close.set(niftyLtp);

                    Candle candle =Candle.builder()
                            .low(low.get())
                            .high(high.get())
                            .open(open.get())
                            .close(close.get())
                            .build();

                    candleList.add(candle);

                    startTime.set(System.currentTimeMillis());
                    open.set(niftyLtp);

                    if (!candleList.isEmpty()) {
                        Candle printt = candleList.get(candleList.size() - 1);
                        System.out.println("close " + printt.getClose());
                        System.out.println("open " + printt.getOpen());
                        System.out.println("high " + printt.getHigh());
                        System.out.println("low " + printt.getLow());
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        };

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(periodicRunnable, 0, 10, TimeUnit.SECONDS);

        /*
        List<Index> niftyList = intializeSymbolTokenMap(smartConnect);
        for (Index ele : niftyList) {
            JSONObject obj = smartConnect.getLTP(ele.getExchSeg(), ele.getSymbol(), ele.getToken());
            ele.setLtp(obj.get("ltp").toString());
            Thread.sleep(100);
        }

         */

        //  writeToS3(niftyList);
    }

    private static HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "AWSALBAPP-0=_remove_; AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; AWSALB=2W/GV5RHWOnI1eKt8uaZZGlniavOP19H51ajcdACjRFKUYg1pXtMdosTOJYBMUm5+t7gFPQCFoYTeyXiJ3BXMHmdNqTcb7RnnW+S6BJaaay0rQtaJbdMrb8ii5NN; AWSALBAPP-0=_remove_; AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; AWSALBCORS=2W/GV5RHWOnI1eKt8uaZZGlniavOP19H51ajcdACjRFKUYg1pXtMdosTOJYBMUm5+t7gFPQCFoYTeyXiJ3BXMHmdNqTcb7RnnW+S6BJaaay0rQtaJbdMrb8ii5NN");
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en,gu;q=0.9,hi;q=0.8");
        headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        headers.add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36");
        headers.add(HttpHeaders.CONNECTION, "keep-alive");

        return headers;
    }

    private static void writeToS3(List<Index> niftyList) {
        try {
            File myObj = new File("/tmp/filename.txt");
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        try {
            FileWriter myWriter = new FileWriter("/tmp/filename.txt");

            for (Index ele : niftyList) {
                String symbol = ele.getSymbol();
                myWriter.write(ele.getExpiryString() + " " + ele.getStrike() +
                        symbol.substring(symbol.length() - 2) + " " + ele.getLtp() + " | ");
            }
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        String bucket_name = "weekly9725";
        String file_path = "/tmp/filename.txt";
        Path path = FileSystems.getDefault().getPath("/tmp", "filename.txt");

        String folder = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String file = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String key_name = folder + "/" + file + ".txt";

        System.out.format("Uploading %s to S3 bucket %s...\n", file_path, bucket_name);

        Region region = Region.US_EAST_2;

        S3Client s3 = S3Client.builder()
                .region(region)
                .build();

        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key_name)
                .build();

        s3.putObject(putOb,
                RequestBody.fromFile(path));


    }

    private static SmartConnect connectWithAngel() {
        SmartConnect smartConnect = new SmartConnect();

        // Provide your api key here
        smartConnect.setApiKey("a5Dd7nxn");

        // Set session expiry callback.
        smartConnect.setSessionExpiryHook(new SessionExpiryHook() {
            @Override
            public void sessionExpired() {
                System.out.println("session expired");
            }
        });

        User user = smartConnect.generateSession("A844782", "9725", getTOTPCode("4TLPUQ4SFZKRXMEYSFRBKGKFOY"));

        String feedToken = user.getFeedToken();
        System.out.println(feedToken);

        System.out.println(user.toString());
        smartConnect.setAccessToken(user.getAccessToken());
        smartConnect.setUserId(user.getUserId());
//
//        // token re-generate
//
//        TokenSet tokenSet = smartConnect.renewAccessToken(user.getAccessToken(),
//                user.getRefreshToken());
//        smartConnect.setAccessToken(tokenSet.getAccessToken());

        return smartConnect;
    }

    public static String getTOTPCode(String secretKey) {
        Base32 base32 = new Base32();
        byte[] bytes = base32.decode(secretKey);
        String hexKey = Hex.encodeHexString(bytes);
        return TOTP.getOTP(hexKey);
    }

    private static List<Index> intializeSymbolTokenMap(SmartConnect smartConnect) {
        HttpEntity<String> request = new HttpEntity<>(getHeaders());

        String url = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);


        String urlTemplate = uriComponentsBuilder.uriVariables(new HashMap<>()).toUriString();
        RestTemplate restTemplate = new RestTemplate();

        DefaultUriBuilderFactory defaultUriBuilderFactory = new DefaultUriBuilderFactory();
        defaultUriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        restTemplate.setUriTemplateHandler(defaultUriBuilderFactory);
        restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        ResponseEntity apiResponse;
        String resource = null;
        try {
            apiResponse = restTemplate.exchange(
                    urlTemplate,
                    HttpMethod.GET,
                    request,
                    String.class
            );
            resource = apiResponse.getBody().toString();
            // System.out.println(resource);
        } catch (Exception e) {
            System.out.println("EXCEPTION WHILE INVOKING API KEY REQUEST : " + e);
        }

        return businessLogic(resource, smartConnect);
    }

    private static List<Index> businessLogic(String resource, SmartConnect smartConnect) {
        JsonArray angelIndexArray = resource == null ? null :
                StringUtils.isBlank(resource) ?
                        null : new Gson().fromJson(resource, JsonArray.class);

        JsonArray niftyArray = new JsonArray();
        for (JsonElement angelElement : angelIndexArray) {
            if (getSingleData(angelElement, "name").equalsIgnoreCase("NIFTY")
                    && (getSingleData(angelElement, "instrumenttype").equalsIgnoreCase("OPTIDX"))) {
                niftyArray.add(angelElement);
            }
        }

        List<Index> niftyList = new ArrayList<>();
        for (JsonElement ele : niftyArray) {
            Index id = new Index();
            try {
                id.setExpiry(formatToUTCTime(getSingleData(ele, "expiry")));
            } catch (Exception e) {
                e.printStackTrace();
            }
            id.setExpiryString(getSingleData(ele, "expiry"));
            id.setInstrumenttype(getSingleData(ele, "instrumenttype"));
            id.setName(getSingleData(ele, "name"));
            id.setLotsize(getSingleData(ele, "lotsize"));
            id.setSymbol(getSingleData(ele, "symbol"));
            Integer strike = ((int) Double.parseDouble(getSingleData(ele, "strike"))) / 100;
            id.setStrike(strike);
            id.setToken(getSingleData(ele, "token"));
            id.setExchSeg(getSingleData(ele, "exch_seg"));

            niftyList.add(id);
        }

        String currStrikePriceString = null;
        currStrikePriceString = getNiftyltp(smartConnect);

        int currStrikePrice = (currStrikePriceString == null || "".equals(currStrikePriceString))
                ? Integer.parseInt(System.getenv("CURR_STRIKE_PRICE")) : Integer.parseInt(currStrikePriceString);


        // filter out only top 5 & last 5 strike prices 17500 - 18500
        // filter out this week and next week expiry

        String reachAbleStrike = System.getenv("REACHABLE_STRIKES");
        int reachAbleStrikeInt = 500;
        try {
            reachAbleStrikeInt = Integer.parseInt(reachAbleStrike);
        } catch (Exception e) {
            reachAbleStrikeInt = 500;
        }
        int finalReachableStrikeInt = reachAbleStrikeInt;
        List<Index> filteredList = niftyList.stream()
                .filter(t -> Math.abs(t.getStrike() - currStrikePrice) < finalReachableStrikeInt)
                .collect(Collectors.toList());


        List<Index> finalList = new ArrayList<>();
        for (Index ele : filteredList) {
            if (isValidExpiry(ele.getExpiry())) {
                finalList.add(ele);
            }
        }
        return finalList;
    }

    private static String getSingleData(JsonElement angelElement, String byName) {
        return initializeParams(((JsonObject) angelElement).get(byName));
    }

    private static String initializeParams(JsonElement element) {
        return element == null || element instanceof JsonNull ? "" : element.getAsString();
    }

    private static Date formatToUTCTime(String timeStamp) throws ParseException {
        SimpleDateFormat dateParser = new SimpleDateFormat("ddMMMyyyy");
        return dateParser.parse(timeStamp);
    }

    private static boolean isValidExpiry(Date expiry) {

        Date today = new Date();

        long difference_In_Time
                = expiry.getTime() - today.getTime();

        long daysToExpire
                = (difference_In_Time
                / (1000 * 60 * 60 * 24))
                % 365;

        // 0-6 | 7-13 | 14-20
        return ((daysToExpire + 1) <= 20);
    }

    private static String getNiftyltp(SmartConnect smartConnect) {
        JSONObject indexObj = smartConnect.getLTP("NSE", "NIFTY", "26000");
        // int niftyLtp = Integer.parseInt(indexObj.get("ltp").toString().substring(0, 5));

        double niftyLtp = Double.parseDouble(indexObj.get("ltp").toString());


        /*
        int mod = (niftyLtp) % 100;
        System.out.println("nifty ltp is " + niftyLtp);
        if (mod > 50) {
            niftyLtp = niftyLtp + (100 - mod);
        } else {
            niftyLtp = niftyLtp - (mod);
        }

         */
        return String.valueOf(niftyLtp);
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("url: " + url);
     //   List<Candle> candleList = morningService.createCandlesData(url);

      //   execute(candleList);
    }


}
