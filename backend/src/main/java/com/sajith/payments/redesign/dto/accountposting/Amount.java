package com.sajith.payments.redesign.dto.accountposting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;

import java.io.IOException;

@Data
@JsonDeserialize(using = Amount.AmountDeserializer.class)
public class Amount {

    @JsonProperty("value")
    private String value;

    @JsonProperty("currency_code")
    private String currency;

    /**
     * Handles both the legacy numeric format (BigDecimal stored as a JSON number)
     * and the current object format {"value": "...", "currency_code": "..."}.
     */
    static class AmountDeserializer extends JsonDeserializer<Amount> {

        @Override
        public Amount deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Amount amount = new Amount();
            JsonToken token = p.currentToken();
            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                amount.setValue(p.getDecimalValue().toPlainString());
            } else {
                ObjectNode node = p.readValueAsTree();
                if (node.has("value")) {
                    amount.setValue(node.get("value").asText());
                }
                if (node.has("currency_code")) {
                    amount.setCurrency(node.get("currency_code").asText());
                }
            }
            return amount;
        }
    }
}
