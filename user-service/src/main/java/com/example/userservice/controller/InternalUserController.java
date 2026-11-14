package com.example.userservice.controller;

import com.example.userservice.model.response.InternalAdminResponse;
import com.example.userservice.model.response.InternalUserLookupResponse;
import com.example.userservice.model.response.InternalUserResponse;
import com.example.userservice.model.response.StrikeResponse;
import com.example.userservice.service.InternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** User lookups and strikes for the other services. Protected by the internal key, see InternalKeyFilter. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalService internalService;

    @GetMapping("/{id}")
    public InternalUserResponse getUser(@PathVariable Long id) {
        return internalService.getUser(id);
    }

    @PostMapping("/names")
    public Map<Long, String> getNames(@RequestBody List<Long> ids) {
        return internalService.getNames(ids);
    }

    @PostMapping("/lookup")
    public Map<Long, InternalUserLookupResponse> lookupUsers(@RequestBody List<Long> ids) {
        return internalService.lookupUsers(ids);
    }

    @PostMapping("/{id}/strikes")
    public StrikeResponse addStrike(@PathVariable Long id) {
        return internalService.addStrike(id);
    }

    @DeleteMapping("/{id}/strikes")
    public StrikeResponse removeStrike(@PathVariable Long id) {
        return internalService.removeStrike(id);
    }

    @GetMapping("/admins")
    public List<InternalAdminResponse> getAdmins() {
        return internalService.getActiveAdmins();
    }
}
