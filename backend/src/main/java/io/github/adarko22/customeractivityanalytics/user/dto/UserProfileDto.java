package io.github.adarko22.customeractivityanalytics.user.dto;

import java.util.List;

public record UserProfileDto(
    String username, String firstName, String lastName, String email, List<String> roles) {}
