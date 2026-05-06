package com.syuuk.patentflow.patent.client;

import com.syuuk.patentflow.patent.dto.PatentBibliographicInfoResponse;
import java.util.Optional;

public interface ExternalPatentLookupClient {

    Optional<PatentBibliographicInfoResponse> lookup(PatentLookupQuery query);
}
