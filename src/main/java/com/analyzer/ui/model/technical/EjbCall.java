package com.analyzer.model.technical;

/**
 * Représente un appel à un Enterprise JavaBean (EJB),
 * souvent utilisé comme passerelle vers des systèmes externes comme le mainframe.
 */
public class EjbCall extends ExternalCall {
    public String jndiName;      // Le nom JNDI recherché (ex: "ejb/com/acme/PaymentGateway")
    public String methodCalled;  // La méthode appelée sur l'EJB (si détectable)
}
