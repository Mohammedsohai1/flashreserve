
package com.example.flashreserve.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing the password reset token after successful OTP verification.")
public record ResetTokenResponse(

        @Schema(
                description = "Temporary token required to authorize the password reset.",
                example = "sample-reset-token"
        )
        String resetToken,

        @Schema(
                description = "Message describing the OTP verification result.",
                example = "OTP verified successfully."
        )
        String message
) {
}
