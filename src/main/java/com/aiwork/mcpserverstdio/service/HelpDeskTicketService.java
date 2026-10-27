package com.aiwork.mcpserverstdio.service;

import com.aiwork.mcpserverstdio.entity.HelpDeskTicket;
import com.aiwork.mcpserverstdio.model.TicketRequest;
import com.aiwork.mcpserverstdio.repository.HelpDeskTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HelpDeskTicketService {
    private final HelpDeskTicketRepository helpDeskTicketRepo;

    public HelpDeskTicket createTicket(TicketRequest req, String username){
        HelpDeskTicket helpDeskTicket = HelpDeskTicket.builder()
                .issue(req.issue())
                .username(username)
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .eta(LocalDateTime.now().plusDays(7))
                .build();
        return helpDeskTicketRepo.save(helpDeskTicket);
    }

    public List<HelpDeskTicket> getTicketByUsername(String username){
        return helpDeskTicketRepo.findByUsername(username);
    }
}
