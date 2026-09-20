package com.ptutor.backend.mapper;

import java.util.stream.Stream;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ptutor.backend.dto.response.PublicReviewResponse;
import com.ptutor.backend.entity.Review;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "displayName", expression = "java(toAnonymousStudentName(review))")
    @Mapping(target = "tutorName", expression = "java(toTutorName(review))")
    @Mapping(target = "tutorAvatarUrl", source = "tutor.user.avatarUrl")
    PublicReviewResponse toPublicResponse(Review review);

    default String toTutorName(Review review) {
        if (review.getTutor() == null || review.getTutor().getUser() == null) {
            return "";
        }
        return joinName(review.getTutor().getUser().getFirstName(), review.getTutor().getUser().getLastName());
    }

    default String toAnonymousStudentName(Review review) {
        if (review.getStudent() == null || review.getStudent().getUser() == null) {
            return "Học viên";
        }
        String firstName = review.getStudent().getUser().getFirstName();
        String lastName = review.getStudent().getUser().getLastName();
        String safeFirstName = firstName == null || firstName.isBlank() ? "Học viên" : firstName.strip();
        String lastInitial = Stream.ofNullable(lastName)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> value.substring(0, 1).toUpperCase() + ".")
                .findFirst()
                .orElse("");
        return joinName(safeFirstName, lastInitial);
    }

    private String joinName(String firstName, String lastName) {
        return Stream.of(firstName, lastName)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
