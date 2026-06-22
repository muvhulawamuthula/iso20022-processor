package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class Party {

    @XmlElement(name = "Nm", required = true)
    private String nm;

    public String getNm() { return nm; }
    public void setNm(String v) { this.nm = v; }
}
