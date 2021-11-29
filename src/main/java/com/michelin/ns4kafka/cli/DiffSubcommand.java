package com.michelin.ns4kafka.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.michelin.ns4kafka.cli.models.ApiResource;
import com.michelin.ns4kafka.cli.models.Resource;
import com.michelin.ns4kafka.cli.services.ApiResourcesService;
import com.michelin.ns4kafka.cli.services.FileService;
import com.michelin.ns4kafka.cli.services.LoginService;
import com.michelin.ns4kafka.cli.services.ResourceService;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "diff", description = "Get differences between the new resources and the old resource")
public class DiffSubcommand implements Runnable {

    @Inject
    public LoginService loginService;
    @Inject
    public ApiResourcesService apiResourcesService;
    @Inject
    public FileService fileService;
    @Inject
    public ResourceService resourceService;

    @Inject
    public KafkactlConfig kafkactlConfig;

    @CommandLine.ParentCommand
    public KafkactlCommand kafkactlCommand;
    @Option(names = {"-f", "--file"}, description = "YAML File or Directory containing YAML resources")
    public Optional<File> file;
    @Option(names = {"-R", "--recursive"}, description = "Enable recursive search of file")
    public boolean recursive;

    @CommandLine.Spec
    public CommandLine.Model.CommandSpec commandSpec;

    @Override
    public void run() {

        boolean authenticated = loginService.doAuthenticate();
        if (!authenticated) {
            throw new CommandLine.ParameterException(commandSpec.commandLine(), "Login failed");
        }

        // 0. Check STDIN and -f
        boolean hasStdin = false;
        try {
            hasStdin = System.in.available() > 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        // If we have none or both stdin and File set, we stop
        if (hasStdin == file.isPresent()) {
            throw new CommandLine.ParameterException(commandSpec.commandLine(), "Required one of -f or stdin");
        }

        List<Resource> resources;

        if (file.isPresent()) {
            // 1. list all files to process
            List<File> yamlFiles = fileService.computeYamlFileList(file.get(), recursive);
            if (yamlFiles.isEmpty()) {
                throw new CommandLine.ParameterException(commandSpec.commandLine(), "Could not find yaml/yml files in " + file.get().getName());
            }
            // 2 load each files
            resources = fileService.parseResourceListFromFiles(yamlFiles);
        } else {
            Scanner scanner = new Scanner(System.in);
            scanner.useDelimiter("\\Z");
            // 2 load STDIN
            resources = fileService.parseResourceListFromString(scanner.next());
        }

        // 3. validate resource types from resources
        List<Resource> invalidResources = apiResourcesService.validateResourceTypes(resources);
        if (!invalidResources.isEmpty()) {
            String invalid = String.join(", ", invalidResources.stream().map(Resource::getKind).distinct().collect(Collectors.toList()));
            throw new CommandLine.ParameterException(commandSpec.commandLine(), "The server doesn't have resource type [" + invalid + "]");
        }
        // 4. validate namespace mismatch
        String namespace = kafkactlCommand.optionalNamespace.orElse(kafkactlConfig.getCurrentNamespace());
        List<Resource> nsMismatch = resources.stream()
                .filter(resource -> resource.getMetadata().getNamespace() != null && !resource.getMetadata().getNamespace().equals(namespace))
                .collect(Collectors.toList());
        if (!nsMismatch.isEmpty()) {
            String invalid = String.join(", ", nsMismatch.stream().map(resource -> resource.getKind() + "/" + resource.getMetadata().getName()).distinct().collect(Collectors.toList()));
            throw new CommandLine.ParameterException(commandSpec.commandLine(), "Namespace mismatch between kafkactl and yaml document [" + invalid + "]");
        }
        List<ApiResource> apiResources = apiResourcesService.getListResourceDefinition();

        // 5. process each document individually, return 0 when all succeed
        int errors = resources.stream()
                .map(resource -> {
                    ApiResource apiResource = apiResources.stream()
                            .filter(apiRes -> apiRes.getKind().equals(resource.getKind()))
                            .findFirst()
                            .orElseThrow(); // already validated
                    Resource live = resourceService.getSingleResourceWithType(apiResource, namespace, resource.getMetadata().getName(), false);
                    HttpResponse<Resource> merged = resourceService.apply(apiResource, namespace, resource, true);
                    if (merged != null) {
                        List<String> uDiff = unifiedDiff(live, merged.body());
                        uDiff.forEach(System.out::println);
                        return 0;
                    }
                    return 1;
                })
                .mapToInt(Integer::valueOf)
                .sum();
    }

    private List<String> unifiedDiff(Resource live, Resource merged) {
        // ignore status and timestamp for comparison
        if (live != null) {
            live.setStatus(null);
            live.getMetadata().setCreationTimestamp(null);
        }
        merged.setStatus(null);
        merged.getMetadata().setCreationTimestamp(null);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        List<String> oldResourceStr = null;
        List<String> newResourceStr = null;
        try {
            oldResourceStr = live != null ? mapper.writeValueAsString(live).lines().collect(Collectors.toList()) : List.of();
            newResourceStr = mapper.writeValueAsString(merged).lines().collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            oldResourceStr=List.of();
            newResourceStr=List.of();
        }

        return List.of("DISABLED");

    }
}
