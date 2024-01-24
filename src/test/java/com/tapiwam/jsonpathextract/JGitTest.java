package com.tapiwam.jsonpathextract;

import com.tapiwam.jsonpathextract.model.GitMergeView;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


@Slf4j
public class JGitTest {

    void testGit1() throws GitAPIException, IOException {
        String base = "/Users/tapiwamaruni/Documents/projects/git1/";
        String name = UUID.randomUUID().toString().substring(0, 5);

        String localRepo = base + name;
        File localRepoDir = new File(localRepo);
        try {
            if (!localRepoDir.exists()) {
                Files.createDirectories(localRepoDir.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Git git = Git.init().setDirectory(localRepoDir).call();
        log.info("GIT repo at : " + localRepoDir);
    }

    @Test
    void testGit() throws GitAPIException, IOException {
        String localRepo = "/Users/tapiwamaruni/Documents/projects/git1/ce8ec";
        File localRepoDir = new File(localRepo);

        String fileName = UUID.randomUUID().toString().substring(0,5) + ".txt";

        Git git = Git.open( localRepoDir );
        Ref master1 = checkoutBranch(git, "master");
        log.info("GIT repo at : {} - {}", localRepoDir, master1);

        Path f1 = Paths.get(localRepo, fileName);
        Files.write(f1, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));

        git.add().addFilepattern(fileName).call();
        gitStatus(git);

        RevCommit commit = git.commit().setMessage(fileName + " OG").call();
        log.info("Commit file: {} - {} - {}", fileName, commit.getId().name(), commit.getFullMessage());
        gitStatus(git);

        // Checkout new branch test, modify and commit
        Ref testBranch = checkoutBranch(git, "test");
        Files.write(f1, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        gitStatus(git);
        git.add().addFilepattern(fileName).call();
        gitStatus(git);
        RevCommit commitTest = git.commit().setMessage(fileName + " in TEST").call();
        gitStatus(git);
        log.info("Commit file: {} - {} - {}", fileName, commitTest.getId().name(), commitTest.getFullMessage());


        // Add new files
        Ref masterBranch = checkoutBranch(git, "master");
        Files.write(f1, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        gitStatus(git);
        git.add().addFilepattern(fileName).call();
        gitStatus(git);
        RevCommit commitMaster = git.commit().setMessage(fileName + " in MASTER").call();
        gitStatus(git);
        log.info("Commit file: {} - {} - {}", fileName, commitMaster.getId().name(), commitMaster.getFullMessage());

//        // Merge
//        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository(), true);
////        boolean canMerge = merger.merge(commitMaster, commitTest);
//        boolean canMerge = merger.merge(git.getRepository().resolve( "master" ), git.getRepository().resolve( "test" ));
//        log.info("Commit can merge TEST -> MASTER : @file={} @canMerge={}", fileName, canMerge);
//
//        checkoutBranch(git, "master");
//        ObjectId master = git.getRepository().resolve( "HEAD" );
//        RevCommit masterlatest = git.log().setMaxCount(1).call().iterator().next();
//
//        log.info("MASTER: {} - {}", master, masterlatest);
//
//        checkoutBranch(git, "test");
//        ObjectId test = git.getRepository().resolve( "HEAD" );
//        RevCommit testlatest = git.log().setMaxCount(1).call().iterator().next();
//        log.info("TEST: {} - {}", test, testlatest);


        // Merge
        checkoutBranch(git, "master");
        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository(), true);
        boolean canMerge = merger.merge(commitMaster, commitTest, commitTest.getId());

        log.info("Commit can merge TEST -> MASTER : @canMerge={}", canMerge);

        MergeResult res = git.merge()
                .setCommit(true)
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .include(commitTest)
                .call();
        log.info("Merge TEST -> MASTER : @res={}", res);
        Assertions.assertEquals(MergeResult.MergeStatus.CONFLICTING, res.getMergeStatus());

        log.info("Conflicts: {}" , res.getConflicts());

        for (Map.Entry<String,int[][]> entry : res.getConflicts().entrySet()) {
            System.out.println("Key: " + entry.getKey());
            for(int[] arr : entry.getValue()) {
                System.out.println("value: " + Arrays.toString(arr));
            }
        }


        Set<String> conflictingPaths = res.getConflicts().keySet();

        List<File> result = new ArrayList<>();
        List<GitMergeView> con = new ArrayList<>();

        for (String path : conflictingPaths) {
            File conflictFile = new File(git.getRepository().getDirectory().getParent(), path);
            result.add(conflictFile);

            // For each conflict file get before and after views
            String conflictContent = Files.readString(conflictFile.toPath());
            GitMergeView v = GitMergeView.builder()
                    .relativePath(path)
                    .fileName(conflictFile.getName())
                    .conflict(conflictContent)
                    .selection(GitMergeView.GitMergeSelection.AFTER)
                    .build();

            v.splitConflict();
            con.add(v);

            System.out.println("======= CONFLICT FILE " + path + " ==========");
            System.out.println(v);
        }

        // After choosing before/after replace files with chosen content and add
        for (GitMergeView conflict: con) {
            Path file = Paths.get(git.getRepository().getDirectory().toPath().toString(), conflict.getRelativePath());
            String selected = null;

            if(GitMergeView.GitMergeSelection.AFTER.equals(conflict.getSelection())){
                selected = conflict.getAfter();
            } else {
                selected = conflict.getBefore();
            }

            // Override file
            Files.write(file, selected.getBytes(StandardCharsets.UTF_8));

            // Add
            git.add().addFilepattern(conflict.getRelativePath()).call();
        }

        gitStatus(git);

        // Commit agan
        RevCommit mergeCommit = git.commit().call();
        log.info("FINAL Commit merge TEST -> MASTER : @mergeCommit={}", mergeCommit);
    }


    @Test
    void testGitMerge() throws GitAPIException, IOException {
        String localRepo = "/Users/tapiwamaruni/Documents/projects/git1/ce8ec";
        File localRepoDir = new File(localRepo);

        Git git = Git.open( localRepoDir );
        log.info("GIT repo at : {}", localRepoDir);

        checkoutBranch(git, "master");
        ObjectId master = git.getRepository().resolve( "HEAD" );
        RevCommit masterlatest = git.log().setMaxCount(1).call().iterator().next();

        log.info("MASTER: {} - {}", master, masterlatest);

        checkoutBranch(git, "test");
        ObjectId test = git.getRepository().resolve( "HEAD" );
        RevCommit testlatest = git.log().setMaxCount(1).call().iterator().next();
        log.info("TEST: {} - {}", test, testlatest);


        // Merge
        checkoutBranch(git, "master");
        ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository(), true);
        boolean canMerge = merger.merge(masterlatest, testlatest);

        log.info("Commit can merge TEST -> MASTER : @canMerge={}", canMerge);
    }

    Ref checkoutBranch(Git git, String branch) throws GitAPIException {
        Status status = git.status().call();
        if(!status.isClean()){
            log.warn("Branch not clean before checkout. Reset branch. @branch={}", branch);
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
            log.warn("Reset HEAD before checkout. @branch={}", branch);
        }

        Ref testBranch = null;
        try {
            testBranch  = git.checkout().setName(branch).setCreateBranch(false).call();
            log.info("Branch already exists. Checkout branch done. @branch={} @ref={}", branch, testBranch);
        } catch (Exception e){
            log.error("Creating branch with checkout. @branch={} @err={}", branch, e.getMessage());
            testBranch  = git.checkout().setName(branch).setCreateBranch(true).call();

            log.info("Branch created on checkout. @branch={} @ref={}", branch, testBranch);
        }

        return testBranch;
    }

    void gitStatus(Git git) throws GitAPIException {
        Status status = git.status().call();

        System.out.println("----- GIT STATUS -------");
        System.out.println("IS CLEAN - " + status.isClean());
        status.getAdded().forEach(f -> System.out.println("Added: " + f));
        status.getRemoved().forEach(f -> System.out.println("Removed: " + f));
        status.getMissing().forEach(f -> System.out.println("Missing: " + f));
        status.getChanged().forEach(f -> System.out.println("Changed: " + f));
        status.getUncommittedChanges().forEach(f -> System.out.println("Uncommitted: " + f));
        status.getUntracked().forEach(f -> System.out.println("Untracked: " + f));
        status.getConflicting().forEach(f -> System.out.println("Conflict: " + f));
        System.out.println("----- GIT STATUS -------");
    }

}
