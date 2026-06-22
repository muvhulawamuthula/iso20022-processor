package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class CreditTransferTransaction {

    @XmlElement(name = "PmtId", required = true)
    private PaymentIdentification pmtId;

    @XmlElement(name = "IntrBkSttlmAmt", required = true)
    private ActiveCurrencyAndAmount intrBkSttlmAmt;

    @XmlElement(name = "ChrgBr")
    private String chrgBr;

    @XmlElement(name = "Dbtr", required = true)
    private Party dbtr;

    @XmlElement(name = "DbtrAcct", required = true)
    private CashAccount dbtrAcct;

    @XmlElement(name = "Cdtr", required = true)
    private Party cdtr;

    @XmlElement(name = "CdtrAcct", required = true)
    private CashAccount cdtrAcct;

    public PaymentIdentification getPmtId() { return pmtId; }
    public void setPmtId(PaymentIdentification v) { this.pmtId = v; }
    public ActiveCurrencyAndAmount getIntrBkSttlmAmt() { return intrBkSttlmAmt; }
    public void setIntrBkSttlmAmt(ActiveCurrencyAndAmount v) { this.intrBkSttlmAmt = v; }
    public String getChrgBr() { return chrgBr; }
    public void setChrgBr(String v) { this.chrgBr = v; }
    public Party getDbtr() { return dbtr; }
    public void setDbtr(Party v) { this.dbtr = v; }
    public CashAccount getDbtrAcct() { return dbtrAcct; }
    public void setDbtrAcct(CashAccount v) { this.dbtrAcct = v; }
    public Party getCdtr() { return cdtr; }
    public void setCdtr(Party v) { this.cdtr = v; }
    public CashAccount getCdtrAcct() { return cdtrAcct; }
    public void setCdtrAcct(CashAccount v) { this.cdtrAcct = v; }
}
