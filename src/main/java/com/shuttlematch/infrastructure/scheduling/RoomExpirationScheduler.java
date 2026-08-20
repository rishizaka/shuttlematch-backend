package com.shuttlematch.infrastructure.scheduling;

import com.shuttlematch.application.usecase.room.CloseExpiredRoomsUseCase;

import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 期限切れルームの自動終了を定期実行するスケジューラ。
 * 毎時0分にチェックする。日次(00:00)だと最大約23時間遅れて終了することになり、
 * TTL(36時間)に対して無視できない誤差になるため、毎時にしている。
 */
@Component
public class RoomExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomExpirationScheduler.class);

    private final CloseExpiredRoomsUseCase closeExpiredRooms;

    public RoomExpirationScheduler(CloseExpiredRoomsUseCase closeExpiredRooms) {
        this.closeExpiredRooms = closeExpiredRooms;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        int closed = closeExpiredRooms.execute(OffsetDateTime.now());
        if (closed > 0) {
            log.info("期限切れルームを {} 件終了しました", closed);
        }
    }
}
