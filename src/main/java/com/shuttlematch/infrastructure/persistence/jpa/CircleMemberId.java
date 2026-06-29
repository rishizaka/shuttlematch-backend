package com.shuttlematch.infrastructure.persistence.jpa;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * circle_members の複合主キー(circle_id, user_id)。
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CircleMemberId implements Serializable {

    private UUID circleId;
    private UUID userId;
}
