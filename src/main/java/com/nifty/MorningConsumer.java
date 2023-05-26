package com.nifty;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.nifty.dto.Candle;
import io.awspring.cloud.messaging.listener.SqsMessageDeletionPolicy;
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MorningConsumer {


    public void receiveMessage(String event) {
        JSONObject jsonObject = new JSONObject(event);
        log.info("msg recieved as " , event);

        String details = jsonObject.getJSONObject("data").getJSONArray("candles").toString();

        List<Candle> candleList = new ArrayList<>();
        JsonArray kiteResponse = details == null ? null :
                StringUtils.isBlank(details) ?
                        null : new Gson().fromJson(details, JsonArray.class);
        int i = 0;

        for (JsonElement kiteElement : kiteResponse) {

            JsonArray candleDetailsArray = kiteElement.getAsJsonArray();

            for (JsonElement candleDetails : candleDetailsArray) {

                Candle candle = Candle.builder().build();

                switch (i) {
                    case 0:
                        candle.setDate(candleDetails.getAsString());
                    case 1:
                        candle.setOpen(candleDetails.getAsDouble());
                    case 2:
                        candle.setHigh(candleDetails.getAsDouble());
                    case 3:
                        candle.setLow(candleDetails.getAsDouble());
                    case 4:
                        candle.setClose(candleDetails.getAsDouble());
                }

                candleList.add(candle);
                i++;

            }
            i = 0;
        }

    }
}
