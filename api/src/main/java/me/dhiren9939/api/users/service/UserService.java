package me.dhiren9939.api.users.service;

import lombok.AllArgsConstructor;
import me.dhiren9939.api.users.entity.User;
import me.dhiren9939.api.users.repo.UserRepo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {
    private final UserRepo userRepo;

    public User findOrCreateUser(OAuth2User oAuth2User) {
        String googleSub = oAuth2User.getName(); // "sub" for OIDC/Google principal name

        return userRepo.findByGoogleSub(googleSub)
                .orElseGet(() -> createUser(googleSub, oAuth2User));
    }

    public User getById(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user with id " + userId));
    }

    private User createUser(String googleSub, OAuth2User oAuth2User) {
        User user = new User();
        user.setGoogleSub(googleSub);
        user.setEmail(oAuth2User.getAttribute("email"));
        user.setFirstName(oAuth2User.getAttribute("given_name"));
        user.setLastName(oAuth2User.getAttribute("family_name"));

        try {
            return userRepo.save(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent first-login for the same account (unique googleSub).
            // The other request already inserted the row - fetch it instead of failing.
            return userRepo.findByGoogleSub(googleSub)
                    .orElseThrow(() -> e);
        }
    }
}
