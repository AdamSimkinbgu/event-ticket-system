package com.ticketing.system.Core.Application.dto;

// A single match from the admin "Send Messages" recipient search (search members by username).
public record MemberSearchResultDTO(int memberId, String username) {}
