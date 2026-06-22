package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class FIToFICustomerCreditTransfer {

    @XmlElement(name = "GrpHdr", required = true)
    private GroupHeader grpHdr;

    @XmlElement(name = "CdtTrfTxInf", required = true)
    private List<CreditTransferTransaction> cdtTrfTxInf = new ArrayList<>();

    public GroupHeader getGrpHdr() { return grpHdr; }
    public void setGrpHdr(GroupHeader v) { this.grpHdr = v; }
    public List<CreditTransferTransaction> getCdtTrfTxInf() { return cdtTrfTxInf; }
    public void setCdtTrfTxInf(List<CreditTransferTransaction> v) { this.cdtTrfTxInf = v; }
}
