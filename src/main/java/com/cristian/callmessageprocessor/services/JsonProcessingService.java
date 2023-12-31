package com.cristian.callmessageprocessor.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cristian.callmessageprocessor.models.CallRecord;
import com.cristian.callmessageprocessor.models.Records;
import com.cristian.callmessageprocessor.models.TextRecord;
import com.cristian.callmessageprocessor.utils.MessageValidators;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class JsonProcessingService {

    private final ObjectMapper objectMapper;

    public Records processJson(String jsonContent) throws IOException {
        objectMapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);
        //Create a new Records instance to store procesed messages
        Records messageLog = new Records();

        //Lists to store valid Call, Text and invalid messages
        List<CallRecord> callRecords = new ArrayList<>();
        List<TextRecord> textRecords = new ArrayList<>();
        List<Map<String, Object>> invalidRecords = new ArrayList<>();
        
        //Split the JSON content into individual lines
        String[] jsonLines = jsonContent.split("\n");
        log.info("Total registers readed: {}", jsonLines.length);
        int invalidCount = 0;
        //Loop through each line of JSON   
        for (int i = 0; i < jsonLines.length; i++) {

            boolean isValid = true;
            JsonNode node = null;          
            try {
                //Parse the JSON line into a JsonNode
                node = objectMapper.readTree(jsonLines[i]);
                String messageType = node.path("message_type").asText();

                switch(messageType) {
                    case "CALL":    
                        //Convert the JSON into a CallMessage object
                        CallRecord callMessage = objectMapper.convertValue(node, CallRecord.class);
                        //Check if the CallMessage is valid
                        if (!MessageValidators.isCallMessageValid(callMessage)) {
                            throw new IllegalArgumentException();
                        }
                        //Add the valid CallMessage to the list
                        callRecords.add(callMessage);
                        break;
                    case "MSG":
                        //Convert the JSON into a TextMessage object
                        TextRecord textMessage = objectMapper.convertValue(node, TextRecord.class);
                        //Check if the TextMessage is valid
                        if (!MessageValidators.isTextMessageValid(textMessage)) {
                            throw new IllegalArgumentException();
                        }
                        //Add the valid TextMessage to the list
                        textRecords.add(textMessage);
                        break;
                    default:
                        isValid = false;
                }
            } catch (IllegalArgumentException e) {
                isValid = false;
            } catch (JsonParseException e) {
                invalidCount++;
                log.warn("JSON invalid format at row " + (i + 1) + ": " + jsonLines[i]);
                continue;
            }
            // If the message is not valid, convert it to a Map and add it to the invalidMessages list
            if (!isValid) {
                log.error("ROW NUMBER " + (i + 1) + " -> Invalid message");
                invalidCount++;
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
                //Convert the JsonNode into a Map
                Map<String, Object> invalidMessage = objectMapper.convertValue(node, typeRef);
                invalidRecords.add(invalidMessage);
            }
        }
        if (invalidCount > 0) log.error(invalidCount + " messages are invalid");
        // Set the lists of valid and invalid messages in the MessagesRecord
        messageLog.setCallRecords(callRecords);
        messageLog.setTextRecords(textRecords);
        messageLog.setInvalidRecords(invalidRecords);

        return messageLog;
    }
}
