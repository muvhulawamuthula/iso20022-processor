package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentIdentification {

    @XmlElement(name = "InstrId")
    private String instrId;

    @XmlElement(name = "EndToEndId", required = true)
    private String endToEndId;

    @XmlElement(name = "TxId", required = true)
    private String txId;

    public String getInstrId() { return instrId; }
    public void setInstrId(String v) { this.instrId = v; }
    public String getEndToEndId() { return endToEndId; }
    public void setEndToEndId(String v) { this.endToEndId = v; }
    public String getTxId() { return txId; }
    public void setTxId(String v) { this.txId = v; }
}
