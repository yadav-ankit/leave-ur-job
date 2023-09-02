package com.nifty.util;

import com.angelbroking.smartapi.SmartConnect;
import com.google.gson.*;
import com.nifty.dto.Candle;
import com.trading.Index;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class ServiceUtil {

    public List<Candle> extractLastNCandles(List<Candle> candleList,int n){
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


    public double extractTrueRange(List<Candle> candleList,int n) {

        // true_range = max (h-l,abs(h-pc),abs(l-pc));

        List<Candle> lastNCandles = extractLastNCandles(candleList,n);

        double trueRange = 0;
        for (int i = 1; i < lastNCandles.size(); i++) {
            Candle currentCandle = lastNCandles.get(i);
            Candle prevCandle = lastNCandles.get(i - 1);

            double h_l = currentCandle.high - currentCandle.low;
            double h_pc = Math.abs(currentCandle.high - prevCandle.close);
            double l_pc = Math.abs(currentCandle.low - prevCandle.close);

            double tr = Math.max(h_l, Math.max(h_pc, l_pc));

            tr = tr * 10000;
            tr = Math.round(tr);
            tr = tr / 10000;

            trueRange = tr;
        }
        return trueRange;
    }


    public void setAverageTrueRange(List<Candle> candleList, int n, int index) {
        double sum = 0;

        for (int i = index - n; i < index; i++) {
            sum = sum + candleList.get(i).atr;
        }

        candleList.get(index-1).atr = (sum / n);
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


    private void unusedMethod(){
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

      //  currStrikePriceString = getNiftyltp(smartConnect);

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


}
