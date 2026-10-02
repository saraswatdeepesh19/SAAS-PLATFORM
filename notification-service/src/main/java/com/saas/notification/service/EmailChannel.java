package com.saas.notification.service;

import com.saas.common.events.InvoiceGeneratedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailChannel {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailChannel(JavaMailSender mailSender, @Value("${notification.mail-from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(InvoiceGeneratedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(event.recipientEmail());
        message.setSubject("Invoice " + event.invoiceNumber() + " for " + event.period());
        message.setText("Your invoice " + event.invoiceNumber() + " for " + event.period()
                + " totals " + event.currency() + " " + event.totalAmount() + ".\n"
                + "Invoice ID: " + event.invoiceId());
        mailSender.send(message);
    }
}