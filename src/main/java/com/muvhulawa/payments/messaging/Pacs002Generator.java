package com.muvhulawa.payments.messaging;

import com.muvhulawa.payments.domain.model.TransactionOutcome;
import com.muvhulawa.payments.domain.model.TransactionStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Generates the pacs.002 status report from the original pacs.008 via XSLT. The compiled
 * stylesheet ({@link Templates}) is thread-safe and built once; each call gets its own
 * Transformer (which is stateful and not thread-safe).
 *
 * <p>A pacs.008 batch can carry many transactions, each with its own verdict, so a single
 * group-wide status parameter is not enough. Instead the Java side decides every per-transaction
 * verdict and hands the stylesheet a small {@code <verdicts>} node-set keyed by {@code TxId};
 * the XSLT looks each transaction up by id and renders its {@code TxSts} (and reason, if any).
 * The Java side decides the verdicts, the XSLT only renders them — the same division of labour
 * the rest of the system follows.
 */
@Component
public class Pacs002Generator {

    private static final String XSLT_PATH = "xslt/pacs008-to-pacs002.xslt";
    private final Templates templates;
    private final DocumentBuilderFactory documentBuilderFactory;

    public Pacs002Generator() {
        try (InputStream xslt = new ClassPathResource(XSLT_PATH).getInputStream()) {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            this.templates = factory.newTemplates(new StreamSource(xslt));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compile pacs.002 stylesheet", e);
        }
        this.documentBuilderFactory = hardenedDocumentBuilderFactory();
    }

    /**
     * Render the pacs.002 for a processed batch: the derived group status plus a per-transaction
     * verdict for every {@code CdtTrfTxInf} in the original message.
     */
    public String generate(String originalPacs008, TransactionStatus groupStatus,
                           List<TransactionOutcome> outcomes) {
        return transform(originalPacs008, groupStatus, buildVerdicts(outcomes));
    }

    /** For messages too malformed to transform: build a minimal RJCT status directly. */
    public String generateSchemaReject(com.muvhulawa.payments.domain.model.ReasonCode reasonCode) {
        return Pacs002Fallback.schemaReject(reasonCode);
    }

    /**
     * The sentinel URI the stylesheet passes to {@code document()} to reach the verdict node-set.
     * We resolve it in-process to a {@link DOMSource} — nothing is ever fetched from the network —
     * which is the reliable way to hand XSLTC a node-set it treats natively (a raw DOM passed as a
     * stylesheet parameter is rejected as "Invalid conversion ... to node-set").
     */
    static final String VERDICTS_URI = "verdicts:current";

    private String transform(String sourceXml, TransactionStatus groupStatus, Document verdicts) {
        try {
            Transformer transformer = templates.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setParameter("grpStatus", groupStatus.name());
            transformer.setParameter("creDtTm",
                    LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
            transformer.setURIResolver((href, base) ->
                    VERDICTS_URI.equals(href) ? new DOMSource(verdicts) : null);
            StringWriter out = new StringWriter();
            transformer.transform(new StreamSource(new StringReader(sourceXml)),
                    new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate pacs.002", e);
        }
    }

    /**
     * Builds the verdict node-set the stylesheet looks transactions up in:
     * {@code <verdicts><v id="TxId" sts="ACSC|RJCT" rsn="AM02"/>...</verdicts>}.
     */
    private Document buildVerdicts(List<TransactionOutcome> outcomes) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("verdicts");
            doc.appendChild(root);
            for (TransactionOutcome o : outcomes) {
                Element v = doc.createElement("v");
                v.setAttribute("id", o.transactionId());
                v.setAttribute("sts", o.status().name());
                if (o.reasonCode() != null) {
                    v.setAttribute("rsn", o.reasonCode().name());
                }
                root.appendChild(v);
            }
            return doc;
        } catch (Exception e) {
            throw new IllegalStateException("Could not build pacs.002 verdict node-set", e);
        }
    }

    private static DocumentBuilderFactory hardenedDocumentBuilderFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setExpandEntityReferences(false);
        } catch (Exception ignored) {
            // The verdict document is built entirely in-memory from trusted values; hardening is
            // belt-and-braces, so a parser that doesn't support the feature is not fatal.
        }
        return dbf;
    }
}
