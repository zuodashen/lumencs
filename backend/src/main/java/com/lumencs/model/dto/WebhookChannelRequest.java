package com.lumencs.model.dto;

import lombok.Data;

@Data
public class WebhookChannelRequest {
    private String name;
    private String url;
    private Boolean enabled;
}
