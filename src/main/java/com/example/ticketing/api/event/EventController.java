package com.example.ticketing.api.event;

import com.example.ticketing.api.dto.EventResponse;
import com.example.ticketing.application.event.EventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public List<EventResponse> events() {
        return eventService.findAll();
    }

    @GetMapping("/{eventId}")
    public EventResponse event(@PathVariable Long eventId) {
        return eventService.findById(eventId);
    }
}
