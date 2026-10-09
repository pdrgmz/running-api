package com.running.api.repository.projection;

import java.time.LocalDate;

public interface DailyTrimpProjection {
    LocalDate getDate();
    Double getTotalTrimp();
}
