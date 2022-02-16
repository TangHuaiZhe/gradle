/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.RelativePathFingerprintingStrategy;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

public class OutputFileChanges implements ChangeContainer {

    private static final Interner<String> NOOP_STRING_INTERNER = sample -> sample;

    private final SortedMap<String, FileSystemSnapshot> previous;
    private final SortedMap<String, FileSystemSnapshot> current;

    public OutputFileChanges(SortedMap<String, FileSystemSnapshot> previous, SortedMap<String, FileSystemSnapshot> current) {
        this.previous = previous;
        this.current = current;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileSystemSnapshot, FileSystemSnapshot>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileSystemSnapshot previous, FileSystemSnapshot current) {
                if (previous == current) {
                    return true;
                }
                String propertyTitle = "Output property '" + property + "'";
                if (previous == FileSystemSnapshot.EMPTY) {
                    return reportAllAsAdded(current, propertyTitle, visitor);
                } else if (current == FileSystemSnapshot.EMPTY) {
                    return reportAllAsRemoved(previous, propertyTitle, visitor);
                }
                FileSystemLocationSnapshot previousSnapshot = (FileSystemLocationSnapshot) previous;
                FileSystemLocationSnapshot currentSnapshot = (FileSystemLocationSnapshot) current;
                if (previousSnapshot.getHash().equals(currentSnapshot.getHash())) {
                    // As with relative path, we compare the name of the roots if they are regular files.
                    if (previousSnapshot.getType() != FileType.RegularFile
                        || previousSnapshot.getName().equals(currentSnapshot.getName())) {
                        return true;
                    }
                } else {
                    if (previousSnapshot.getType() == FileType.Missing) {
                        return reportAllAsAdded(currentSnapshot, propertyTitle, visitor);
                    } else if (currentSnapshot.getType() == FileType.Missing) {
                        return reportAllAsRemoved(previousSnapshot, propertyTitle, visitor);
                    }
                }
                RelativePathFingerprintingStrategy relativePathFingerprintingStrategy = new RelativePathFingerprintingStrategy(NOOP_STRING_INTERNER, DirectorySensitivity.DEFAULT);
                CurrentFileCollectionFingerprint previousFingerprint = DefaultCurrentFileCollectionFingerprint.from(previous, relativePathFingerprintingStrategy, null);
                CurrentFileCollectionFingerprint currentFingerprint = DefaultCurrentFileCollectionFingerprint.from(current, relativePathFingerprintingStrategy, null);
                return NormalizedPathFingerprintCompareStrategy.INSTANCE.visitChangesSince(previousFingerprint,
                    currentFingerprint,
                    propertyTitle,
                    visitor);
            }
        });
    }

    private boolean reportAllAsAdded(FileSystemSnapshot currentSnapshot, String propertyTitle, ChangeVisitor visitor) {
        SnapshotVisitResult visitResult = currentSnapshot.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
            DefaultFileChange fileChange = DefaultFileChange.added(
                snapshot.getAbsolutePath(),
                propertyTitle,
                snapshot.getType(),
                relativePath.toRelativePath()
            );
            return visitor.visitChange(fileChange)
                ? SnapshotVisitResult.CONTINUE
                : SnapshotVisitResult.TERMINATE;
        });
        return visitResult != SnapshotVisitResult.TERMINATE;
    }

    private boolean reportAllAsRemoved(FileSystemSnapshot previousSnapshot, String propertyTitle, ChangeVisitor visitor) {
        SnapshotVisitResult visitResult = previousSnapshot.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
            DefaultFileChange fileChange = DefaultFileChange.removed(
                snapshot.getAbsolutePath(),
                propertyTitle,
                snapshot.getType(),
                relativePath.toRelativePath()
            );
            return visitor.visitChange(fileChange)
                ? SnapshotVisitResult.CONTINUE
                : SnapshotVisitResult.TERMINATE;
        });
        return visitResult != SnapshotVisitResult.TERMINATE;
    }

    private static Map<String, FileSystemLocationSnapshot> index(FileSystemSnapshot snapshot) {
        Map<String, FileSystemLocationSnapshot> index = new LinkedHashMap<>();
        snapshot.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
                // Remove missing roots so they show up as added/removed instead of changed
                if (!(isRoot && snapshot instanceof MissingFileSnapshot)) {
                    index.put(snapshot.getAbsolutePath(), snapshot);
                }
                return SnapshotVisitResult.CONTINUE;
            }
        });
        return index;
    }
}
