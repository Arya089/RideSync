package com.ridesync.tracking.controller;

import com.ridesync.common.exception.ApiException;
import com.ridesync.tracking.service.TrackingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingAccessService accessService;
    private final StringRedisTemplate redis;

    @GetMapping(value = "/{id}/location", produces = MediaType.APPLICATION_JSON_VALUE)
    public String latest(@PathVariable Long id, Authentication authentication) {
        if (accessService.roleFor(authentication.getName(), id) == TrackingAccessService.Role.NONE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No access to this ride's location");
        }
        String json = redis.opsForValue().get(TrackingAccessService.locationKey(id));
        if (json == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No recent location");
        }
        return json;
    }
}