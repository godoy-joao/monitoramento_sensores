package com.empresa.monitoramentosensores.config;


import com.empresa.monitoramentosensores.mqtt.MqttMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.topics}")
    private String[] topics;

    private final MqttMessageHandler mqttMessageHandler;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        MqttClient mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setAutomaticReconnect(true);
        connectOptions.setCleanSession(true);

        if (username != null && !username.isEmpty()) {
            connectOptions.setUserName(username);
            connectOptions.setPassword(password.toCharArray());
        }

        mqttClient.setCallback(mqttMessageHandler);
        mqttClient.connect(connectOptions);

        // Inscrição nos tópicos configurados
        for (String topic : topics) {
            mqttClient.subscribe(topic);
            log.info("Inscrito no tópico MQTT: {}", topic);
        }

        log.info("Cliente MQTT conectado com sucesso ao broker: {}", brokerUrl);
        return mqttClient;
    }
}