package com.muvhulawa.payments.messaging;

import com.muvhulawa.payments.domain.model.CreditTransfer;
import com.muvhulawa.payments.domain.model.PaymentBatch;
import com.muvhulawa.payments.messaging.jaxb.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.List;

/**
 * Unmarshals validated pacs.008 XML into the JAXB model, then maps it into the clean domain
 * {@link PaymentBatch} — the group identifier plus every {@code CdtTrfTxInf} it carries. The
 * JAXBContext is built once and reused (it is thread-safe and costly to create); the Unmarshaller
 * is created per call (it is not thread-safe). We parse via a hardened StAX reader to keep XXE
 * protection consistent with the validation gate.
 */
@Component
public class Pacs008Parser {

    private final JAXBContext jaxbContext;
    private final XMLInputFactory xmlInputFactory;

    public Pacs008Parser() {
        try {
            this.jaxbContext = JAXBContext.newInstance(Pacs008Document.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Could not initialise JAXB context for pacs.008", e);
        }
        this.xmlInputFactory = XMLInputFactory.newFactory();
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /** Maps the group header and every credit-transfer transaction in the message to a batch. */
    public PaymentBatch parse(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));
            Pacs008Document doc = (Pacs008Document)
                    unmarshaller.unmarshal(reader);

            var trf = doc.getFiToFICstmrCdtTrf();
            String messageId = trf.getGrpHdr().getMsgId();

            List<CreditTransfer> transfers = trf.getCdtTrfTxInf().stream()
                    .map(Pacs008Parser::toTransfer)
                    .toList();

            return new PaymentBatch(messageId, transfers);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse pacs.008 message", e);
        }
    }

    private static CreditTransfer toTransfer(CreditTransferTransaction tx) {
        return new CreditTransfer(
                tx.getPmtId().getInstrId(),
                tx.getPmtId().getEndToEndId(),
                tx.getPmtId().getTxId(),
                tx.getIntrBkSttlmAmt().getCcy(),
                tx.getIntrBkSttlmAmt().getValue(),
                tx.getDbtr().getNm(),
                tx.getDbtrAcct().getId(),
                tx.getCdtr().getNm(),
                tx.getCdtrAcct().getId());
    }
}
