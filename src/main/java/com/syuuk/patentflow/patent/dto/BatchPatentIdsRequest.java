package com.syuuk.patentflow.patent.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchPatentIdsRequest(@NotEmpty List<String> patentIds) {}
