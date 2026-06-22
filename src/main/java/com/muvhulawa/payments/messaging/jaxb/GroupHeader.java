package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class GroupHeader {

    @XmlElement(name = "MsgId", required = true)
    private String msgId;

    @XmlElement(name = "CreDtTm", required = true)
    private String creDtTm;

    @XmlElement(name = "NbOfTxs", required = true)
    private String nbOfTxs;

    public String getMsgId() { return msgId; }
    public void setMsgId(String v) { this.msgId = v; }
    public String getCreDtTm() { return creDtTm; }
    public void setCreDtTm(String v) { this.creDtTm = v; }
    public String getNbOfTxs() { return nbOfTxs; }
    public void setNbOfTxs(String v) { this.nbOfTxs = v; }
}
