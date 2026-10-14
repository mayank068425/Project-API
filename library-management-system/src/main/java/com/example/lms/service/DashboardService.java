package com.example.lms.service;

import com.example.lms.dto.response.DashboardResponse;

public interface DashboardService {

    DashboardResponse.Admin adminDashboard();

    DashboardResponse.Librarian librarianDashboard();
}
