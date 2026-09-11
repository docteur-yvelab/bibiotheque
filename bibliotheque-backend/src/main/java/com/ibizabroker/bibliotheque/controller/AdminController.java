package com.ibizabroker.bibliotheque.controller;

import com.ibizabroker.bibliotheque.dao.UsersRepository;
import com.ibizabroker.bibliotheque.entity.Role;
import com.ibizabroker.bibliotheque.entity.Users;
import com.ibizabroker.bibliotheque.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/users")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public Users addUserByAdmin(@RequestBody Users user) {
        // Fix : utiliser le rôle ADHERENT par défaut au lieu du self-assignment
        Role role = new Role();
        role.setRoleName("ADHERENT");
        Set<Role> setRole = new HashSet<>();
        setRole.add(role);
        user.setRole(setRole);
        String password = user.getPassword();
        String encryptPassword = passwordEncoder.encode(password);
        user.setPassword(encryptPassword);
        usersRepository.save(user);
        return user;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    public List<Users> getAllUsers() {
        return usersRepository.findAll();
    }

    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @GetMapping("/users/{id}")
    public ResponseEntity<Users> getUserById(@PathVariable Integer id) {
        Users user = usersRepository.findById(id).orElseThrow(() -> new NotFoundException("User with id "+ id +" does not exist."));
        return ResponseEntity.ok(user);
    }

    @PreAuthorize("hasRole('BIBLIOTHECAIRE')")
    @PutMapping("/users/{id}")
    public ResponseEntity<Users> updateUser(@PathVariable Integer id, @RequestBody Users userDetails) {
        Users user = usersRepository.findById(id).orElseThrow(() -> new NotFoundException("User with id "+ id +" does not exist."));

        user.setName(userDetails.getName());
        user.setRole(userDetails.getRole());
        user.setUsername(userDetails.getUsername());

        Users updatedUser = usersRepository.save(user);
        return ResponseEntity.ok(updatedUser);
    }
}
