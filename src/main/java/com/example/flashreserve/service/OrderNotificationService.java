package com.example.flashreserve.service;


import com.example.flashreserve.event.OrderCreatedEvent;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class OrderNotificationService {


    private final JavaMailSender mailSender;
    private final String fromAddress;


    public OrderNotificationService(
            JavaMailSender mailSender,
            @Value("${app.notifications.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }


    public void sendOrderConfirmation(OrderCreatedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();


        message.setFrom(fromAddress);
        message.setTo(event.customerEmail());
        message.setSubject(
                "FlashReserve order confirmation #" + event.orderId());


        message.setText("""
                Thank you for placing your order with FlashReserve!


                Order ID: %d
                Total amount: %s


                Your order has been created successfully.


                Thank you,
                FlashReserve
                """.formatted(
                event.orderId(),
                event.totalAmount().toPlainString()));


        mailSender.send(message);
    }
}
