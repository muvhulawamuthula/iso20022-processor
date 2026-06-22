package com.muvhulawa.payments.messaging.jaxb;

import jakarta.xml.bind.annotation.*;

@XmlAccessorType(XmlAccessType.FIELD)
public class CashAccount {

    @XmlElement(name = "Id", required = true)
    private String id;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
}
