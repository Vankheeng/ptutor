package com.ptutor.backend.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.ContractResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.Contract;
import com.ptutor.backend.entity.enums.ContractStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.ContractMapper;
import com.ptutor.backend.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminContractService {
    private final ContractRepository contractRepository;
    private final ContractMapper contractMapper;

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> findAll(ContractStatus status, String keyword, Pageable pageable) {
        String term = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim().toLowerCase() + "%";
        Page<Contract> contracts = contractRepository.findAllForAdmin(status, term, pageable);
        return PageResponse.from(contracts, contracts.getContent().stream().map(contractMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ContractResponse findById(UUID id) {
        Contract contract = contractRepository.findDetailedByIdForAdmin(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Contract not found: " + id));
        return contractMapper.toResponse(contract);
    }
}
