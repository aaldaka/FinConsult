package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.model.request.CreateSlotRequest;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.RaceRunner;
import com.example.bookingservice.support.RaceRunner.Result;
import com.example.bookingservice.support.SlotTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL). Do NOT put @Transactional on this class: every thread needs its own
// committed transaction. The advisor is an existing one, the user service is a mock, and only the slots
// the test creates are deleted
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class SlotCreationRaceTest {

    @Autowired AvailabilitySlotService availabilitySlotService;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;

    private SlotTestData data;
    private long advisorId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        advisorId = data.anAdvisorId();
        when(advisorClient.getById(advisorId))
                .thenReturn(new AdvisorInfo(advisorId, 1L, true, "Race Advisor", "Planner", Specialty.GENERAL));
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    private CreateSlotRequest request(LocalDateTime start, LocalDateTime end) {
        CreateSlotRequest request = new CreateSlotRequest();
        request.setStartingTime(start);
        request.setFinishingTime(end);
        return request;
    }

    private long slotsBetween(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM availability_slots WHERE advisor_id = ? AND start_time >= ? AND start_time <= ?",
                Long.class, advisorId, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }

    // the advisor posts two overlapping slots at the same moment: only one may be created
    @RepeatedTest(10)
    void overlappingSlotsCreatedAtOnce() throws Exception {
        // a start time nobody has used: the day after the advisor's latest slot ends, or after now + 5 years if that is later.
        // A random day can already hold a slot from an earlier run, and then both new requests are refused
        LocalDateTime start = data.freeStart(advisorId);
        CreateSlotRequest first = request(start, start.plusHours(1));
        CreateSlotRequest second = request(start.plusMinutes(30), start.plusHours(2)); // overlaps the first

        Result result;
        try {
            result = RaceRunner.runAtOnce(List.of(
                    () -> availabilitySlotService.createSlot(advisorId, first),
                    () -> availabilitySlotService.createSlot(advisorId, second)));
        } finally {
            data.trackSlotsStartingBetween(advisorId, start.minusMinutes(1), start.plusHours(2));
        }

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(1, result.refused());
        assertEquals(1, slotsBetween(start.minusMinutes(1), start.plusHours(2)), "only one of the two overlapping slots may exist");
    }

    // the same with a crowd: eight requests that all overlap, one wins
    @RepeatedTest(5)
    void eightOverlappingSlotsCreatedAtOnce() throws Exception {
        LocalDateTime start = data.freeStart(advisorId);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            CreateSlotRequest overlapping = request(start.plusMinutes(i * 5L), start.plusMinutes(i * 5L + 60));
            tasks.add(() -> availabilitySlotService.createSlot(advisorId, overlapping));
        }

        Result result;
        try {
            result = RaceRunner.runAtOnce(tasks);
        } finally {
            data.trackSlotsStartingBetween(advisorId, start.minusMinutes(1), start.plusHours(3));
        }

        assertEquals(0, result.other());
        assertEquals(1, result.successes());
        assertEquals(7, result.refused());
        assertEquals(1, slotsBetween(start.minusMinutes(1), start.plusHours(3)));
    }

    // slots that do not overlap are all created, the lock only makes them wait for each other
    @RepeatedTest(3)
    void separateSlotsCreatedAtOnceAreAllKept() throws Exception {
        LocalDateTime start = data.freeStart(advisorId);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            CreateSlotRequest separate = request(start.plusHours(i * 2L), start.plusHours(i * 2L + 1));
            tasks.add(() -> availabilitySlotService.createSlot(advisorId, separate));
        }

        Result result;
        try {
            result = RaceRunner.runAtOnce(tasks);
        } finally {
            data.trackSlotsStartingBetween(advisorId, start.minusMinutes(1), start.plusHours(8));
        }

        assertEquals(0, result.other());
        assertEquals(4, result.successes());
        assertEquals(4, slotsBetween(start.minusMinutes(1), start.plusHours(8)));
    }
}
