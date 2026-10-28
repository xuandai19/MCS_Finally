package com.ridehailing.pricingservice.api;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/internal")
public class HealthController {
  @GetMapping("/info")
  public Map<String,String> info() { return Map.of("service","pricing-service","status","UP"); }
}
