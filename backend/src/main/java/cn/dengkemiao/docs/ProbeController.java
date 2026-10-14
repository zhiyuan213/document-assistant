package cn.dengkemiao.docs;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeController {
    @GetMapping({"/__tcb_probe__", "/api/health"})
    public Map<String, String> probe() { return Map.of("status", "ok"); }
}
