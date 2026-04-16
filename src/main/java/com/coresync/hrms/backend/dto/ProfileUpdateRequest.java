package com.coresync.hrms.backend.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String phone;
    private String email;
    private String photoUrl;
}
