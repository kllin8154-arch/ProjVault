package com.projvault.security;

import com.projvault.pkc.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class ProjectOwnershipBackfillRunner implements ApplicationRunner {
    private final ProjectRepository projects;
    private final RbacUserRepository users;
    @Value("${projvault.security.bootstrap.username:admin}") private String adminUsername;

    public ProjectOwnershipBackfillRunner(ProjectRepository projects, RbacUserRepository users) {
        this.projects = projects;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findByUsername(adminUsername).ifPresent(admin -> {
            var unowned = projects.findByOwnerUserIdIsNull();
            unowned.forEach(project -> project.setOwnerUserId(admin.getId()));
            projects.saveAll(unowned);
        });
    }
}
