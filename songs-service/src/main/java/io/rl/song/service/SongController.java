package io.rl.song.service;


import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.rl.song.api.*;
import io.rl.song.model.Song;

import io.rl.song.opentracing.HttpHeadersCarrier;
import io.rl.song.opentracing.RestTemplateOpentracingInterceptor;
import io.rl.song.repository.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RestController
@RequestMapping(value = "/api/song")
public class SongController {

    @Autowired
    private final Tracer tracer;

    private final SongRepository repository;

    private static final String HIT_SERVICE = System.getenv("HITS_SERVICE_URL") != null ? System.getenv("HITS_SERVICE_URL") : "http://localhost:8080";

    Logger logger = LoggerFactory.getLogger(SongController.class);

    public SongController(Tracer tracer, SongRepository repository) {
        this.tracer = tracer;
        this.repository = repository;
        this.restTemplate = this.opentracingRestTemplate(tracer);
    }

    private final RestTemplate restTemplate;

    @GetMapping("/{id}")
    public Response get(@PathVariable("id") Long id, @RequestHeader Map<String, String> httpHeaders) {
        Span span = this.initialSpan(tracer, httpHeaders, "Get By ID");

        try (Scope scope = tracer.scopeManager().activate(span)) {

            Span findByIdSpan = tracer.buildSpan("findById").asChildOf(span).start();
            Optional<Song> song = repository.findById(id);
            findByIdSpan.finish();

            if (song.isPresent()) {
                this.hitSong(song.get());

                return this.createSuccessResponse(this.mapSongToSongResponse(song.get()));
            } else
                return this.createErrorResponse("Error");
        } finally {
            span.finish();
        }
    }

    @GetMapping
    public Response getAll(@RequestHeader Map<String, String> httpHeaders) {
        Span span = this.initialSpan(tracer, httpHeaders, "Get All");
        List<Song> songs;
        try (Scope scope = tracer.scopeManager().activate(span)) {
            songs = repository.findAll();
        } finally {
            span.finish();
        }
        return this.createSuccessResponse(this.mapSongsToSongResponseList(songs));
    }

    @PostMapping(path = "/search", consumes = "application/json", produces = "application/json")
    public Response search(@RequestBody SearchTextRequest searchText, @RequestHeader Map<String, String> httpHeaders) {
        Span span = this.initialSpan(tracer, httpHeaders, "Search");
        Set<Song> songs;
        try (Scope scope = tracer.scopeManager().activate(span)) {

            Span songsByNameSpan = tracer.buildSpan("Postgresql").asChildOf(span).start();
            List<Song> songsByName = repository.findByNameIgnoreCaseContaining(searchText.getText());
            songsByNameSpan.finish();

            Span songsByArtistSpan = tracer.buildSpan("Postgresql").asChildOf(span).start();
            List<Song> songsByArtist = repository.findByArtistIgnoreCaseContaining(searchText.getText());
            songsByArtistSpan.finish();

            songs = Stream.concat(songsByName.stream(), songsByArtist.stream()).collect(Collectors.toSet());



        return this.createSuccessResponse(this.mapSongsToSongResponseList(songs.stream().collect(Collectors.toList())));
        } finally {
            span.finish();
        }
    }

    private List<SongResponse> mapSongsToSongResponseList(List<Song> songs) {
        List<SongResponse> songResponses = new ArrayList<SongResponse>();

        for (Song song : songs) {
            songResponses.add(this.mapSongToSongResponse(song));
        }

        return songResponses;
    }

    private SongResponse mapSongToSongResponse(Song song) {
        SongResponse songResponse = new SongResponse();

        songResponse.setId(song.getId());
        songResponse.setName(song.getName());
        songResponse.setArtist(song.getArtist());
        songResponse.setLyricId(song.getLyricId());
        songResponse.setPopularity(this.getSongPopularity(song));

        return songResponse;
    }

    private String hitSong(Song song) {
        String popularity = null;

        HitRequest hit = new HitRequest();
        HitResponse hitResponse;

        hit.setId(song.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        HttpEntity<HitRequest> entity = new HttpEntity<HitRequest>(hit, headers);

        try {

            hitResponse = restTemplate.exchange(String.format("%s/api/hits", HIT_SERVICE),
                    HttpMethod.POST,
                    entity,
                    HitResponse.class).getBody();

            popularity = hitResponse.getPopularity();
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return popularity;
    }

    private String getSongPopularity(Song song) {


        String popularity = null;

        HitResponse hitResponse;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            hitResponse = restTemplate.getForEntity(
                    String.format("%s/api/popularity/%d", HIT_SERVICE, song.getId()),
                    HitResponse.class).getBody();
             return hitResponse.getPopularity();
    }

    private Response createErrorResponse(String message) {
        Response response = new Response();

        response.setStatus(-1);
        response.setMessage(message);

        return response;
    }

    private Response createSuccessResponse(Object data) {
        Response response = new Response();

        response.setStatus(0);
        response.setData(data);

        return response;
    }


    private RestTemplate opentracingRestTemplate(Tracer tracer) {

        RestTemplate restTemplate = new RestTemplate();

        List<ClientHttpRequestInterceptor> interceptors
                = restTemplate.getInterceptors();
        if (CollectionUtils.isEmpty(interceptors)) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(new RestTemplateOpentracingInterceptor(tracer));
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }

    private Span initialSpan(Tracer tracer, Map<String, String> httpHeaders, String operationName){
        SpanContext parentSpanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapAdapter(httpHeaders));
        Tracer.SpanBuilder spanBuilder = parentSpanCtx == null ? tracer.buildSpan(operationName) : tracer.buildSpan(operationName).asChildOf(parentSpanCtx);
        return spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).start();
    }
}

