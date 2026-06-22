package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

/** Root element &lt;Document&gt; of an ISO 20022 pacs.008.001.08 message. */
@XmlRootElement(name = "Document")
@XmlAccessorType(XmlAccessType.FIELD)
public class Pacs008Document {

    @XmlElement(name = "FIToFICstmrCdtTrf", required = true)
    private FIToFICustomerCreditTransfer fiToFICstmrCdtTrf;

    public FIToFICustomerCreditTransfer getFiToFICstmrCdtTrf() { return fiToFICstmrCdtTrf; }
    public void setFiToFICstmrCdtTrf(FIToFICustomerCreditTransfer v) { this.fiToFICstmrCdtTrf = v; }
}
