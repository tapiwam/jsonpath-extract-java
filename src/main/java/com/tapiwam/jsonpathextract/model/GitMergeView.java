package com.tapiwam.jsonpathextract.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GitMergeView {

    private String before;
    private String after;
    private String conflict;
    private String relativePath;
    private String fileName;
    private GitMergeSelection selection;
    private String commitMessage;

    public void splitConflict(){
        List<String> lines = Arrays.stream(conflict.split(System.lineSeparator())).toList();

        List<String> bef = new ArrayList<>();
        List<String> aft = new ArrayList<>();

        boolean befToggle = false;
        boolean aftToggle = false;
        boolean skipLine = false;

        for (String l: lines) {
            skipLine = false;

            if(l.startsWith("<<<<<<< ")){
                befToggle = true;
                skipLine = true;
                aftToggle = false;
            }

            if(l.equals("=======")){
                befToggle = false;
                skipLine = true;
                aftToggle = true;
            }

            if(l.startsWith(">>>>>>> ")){
                befToggle = true;
                skipLine = true;
                aftToggle = true;
            }

            // Build
            if(!skipLine) {
                if(befToggle){
                    bef.add(l);
                }

                if (aftToggle) {
                    aft.add(l);
                }
            }
        }

        before = bef.stream().collect(Collectors.joining(System.lineSeparator()));
        after = aft.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    public enum GitMergeSelection {
        BEFORE, AFTER
    }
}
