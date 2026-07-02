package com.trekmate.exe.service;

import com.trekmate.exe.dto.request.CreateTourRequest;
import com.trekmate.exe.dto.request.EndTourRequest;
import com.trekmate.exe.dto.request.JoinTourRequest;
import com.trekmate.exe.dto.response.CreateTourResponse;
import com.trekmate.exe.dto.response.EndTourResponse;
import com.trekmate.exe.dto.response.JoinTourResponse;
import com.trekmate.exe.dto.response.MemberListResponse;

public interface ExeTourService {

    CreateTourResponse createTour(CreateTourRequest request);

    JoinTourResponse joinTour(JoinTourRequest request);

    EndTourResponse endTour(EndTourRequest request);

    MemberListResponse getMembers(String tourId);
}
