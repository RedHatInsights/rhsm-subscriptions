package org.candlepin.subscriptions.resteasy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TitlecaseSerializer extends StdSerializer<Enum> {

    public TitlecaseSerializer() {
        this(null);
    }

    private TitlecaseSerializer(Class<Enum> t) {
        super(t);
    }

    @Override
    public void serialize(Enum value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(WordUtils.capitalizeFully(value.toString()));
    }
}
