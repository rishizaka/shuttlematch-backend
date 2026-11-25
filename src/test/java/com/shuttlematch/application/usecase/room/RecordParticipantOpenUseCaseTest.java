package com.shuttlematch.application.usecase.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shuttlematch.application.ResourceNotFoundException;
import com.shuttlematch.domain.model.room.RoomId;
import com.shuttlematch.domain.repository.RoomOpenRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordParticipantOpenUseCaseTest {

    @Test
    @DisplayName("ルームがあれば記録する")
    void records() {
        FakeRoomOpenRepository repo = new FakeRoomOpenRepository(true);
        RoomId roomId = RoomId.newId();

        new RecordParticipantOpenUseCase(repo).execute(roomId);

        assertThat(repo.recorded).containsExactly(roomId);
    }

    @Test
    @DisplayName("ルームが無ければ ResourceNotFoundException(404)")
    void notFound() {
        FakeRoomOpenRepository repo = new FakeRoomOpenRepository(false);

        assertThatThrownBy(() -> new RecordParticipantOpenUseCase(repo).execute(RoomId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class FakeRoomOpenRepository implements RoomOpenRepository {
        private final boolean exists;
        private final List<RoomId> recorded = new ArrayList<>();

        FakeRoomOpenRepository(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean recordParticipantOpen(RoomId roomId) {
            if (!exists) return false;
            recorded.add(roomId);
            return true;
        }
    }
}
