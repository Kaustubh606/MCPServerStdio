package com.aiwork.mcpserverstdio.tools;

import com.aiwork.mcpserverstdio.entity.HelpDeskTicket;
import com.aiwork.mcpserverstdio.model.TicketRequest;
import com.aiwork.mcpserverstdio.service.HelpDeskTicketService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HelpDeskTools {

    private static final Logger logger =
            LoggerFactory.getLogger(HelpDeskTools.class);

    private final HelpDeskTicketService service;

    @McpTool(
            name = "createTicket",
            description = "Create the support ticket"
    )
    String createTicket(
            @McpToolParam(description = "Details to create a support ticket") TicketRequest req,
            McpMeta meta) {

        String username = (String) meta.get("username");
        logger.info("Creating support ticket for user: {} with details: {}", username,req);
        HelpDeskTicket savedTicket = service.createTicket(req, username);

        return "Ticket #" + savedTicket.getId()
                + " created successfully for the user "
                + savedTicket.getUsername();
    }

    @McpTool(
            name = "getTicketStatus",
            description = "Fetch the status of the tickets for the current user"
    )
    List<HelpDeskTicket> getTicketStatus(McpMeta meta) {
        String username = (String) meta.get("username");
        logger.info("Fetching support tickets for user: {}",username );
        List<HelpDeskTicket> ticketList =  service.getTicketByUsername(username);
        logger.info("Support tickets fetched size is {} and list is - ",ticketList.size(),ticketList );
        return ticketList;
    }
}