package com.ptutor.backend.service;

import java.util.UUID;

import com.ptutor.backend.entity.Complaint;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.User;

/**
 * The respondent of a complaint is the contract participant who did not submit it.
 */
final class ComplaintParties {

    private ComplaintParties() {
    }

    static User respondentOf(Complaint complaint) {
        Contract contract = complaint.getContract();
        User studentUser = contract.getStudent().getUser();
        return complaint.getUser().getId().equals(studentUser.getId())
                ? contract.getTutor().getUser()
                : studentUser;
    }

    static boolean isRespondent(Complaint complaint, UUID userId) {
        return !complaint.getUser().getId().equals(userId)
                && userId.equals(respondentOf(complaint).getId());
    }
}
