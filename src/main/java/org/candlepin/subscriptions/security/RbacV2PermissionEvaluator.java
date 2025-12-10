package org.candlepin.subscriptions.security;

import java.io.Serializable;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RbacV2PermissionEvaluator implements PermissionEvaluator {

  public RbacV2PermissionEvaluator() {
    log.info("Build evaluator");
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Object targetDomainObject, Object permission) {
    log.info("Evaluate {}", targetDomainObject);
    var r = new Random();
    return r.nextInt() % 2 == 0;
  }

  @Override
  public boolean hasPermission(
      Authentication authentication, Serializable targetId, String targetType, Object permission) {
    log.info("Evaluate id {}", targetId);
    return false;
  }
}
