package ink.ziip.championshipscore.api.object.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PlayerClientVerifyResponse {
    private String username;
    private String status;
    private boolean verified;
    private String last_check;
    private String matched_rule_set;
}
