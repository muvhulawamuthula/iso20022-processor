package com.muvhulawa.payments.messaging;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;

/**
 * The XSD validation gate. The Schema object is compiled ONCE at startup (it is immutable and
 * thread-safe and expensive to build); a fresh Validator is created per call because Validators
 * are NOT thread-safe. Getting that split wrong is a real production footgun under load.
 */
@Component
public class SchemaValidator {

    private static final String SCHEMA_PATH = "schema/pacs.008.001.08.xsd";
    private Schema schema;

    @PostConstruct
    void init() throws SAXException, java.io.IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        // Harden the parser against XXE — payment ingress is untrusted input.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream xsd = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
            this.schema = factory.newSchema(new StreamSource(xsd));
        }
    }

    public void validate(String xml) {
        Validator validator = schema.newValidator();
        try {
            validator.validate(new StreamSource(new java.io.StringReader(xml)));
        } catch (SAXException | java.io.IOException e) {
            throw new SchemaInvalidException("Message failed pacs.008 XSD validation: "
                    + e.getMessage(), e);
        }
    }
}
