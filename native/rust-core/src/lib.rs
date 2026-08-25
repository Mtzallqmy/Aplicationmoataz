use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WorkspacePathError {
    AbsolutePath,
    ParentTraversal,
    Prefix,
}

pub fn safe_workspace_path(root: &Path, relative: &Path) -> Result<PathBuf, WorkspacePathError> {
    if relative.is_absolute() {
        return Err(WorkspacePathError::AbsolutePath);
    }

    let mut resolved = root.to_path_buf();
    for component in relative.components() {
        match component {
            Component::CurDir => {}
            Component::Normal(part) => resolved.push(part),
            Component::ParentDir => return Err(WorkspacePathError::ParentTraversal),
            Component::Prefix(_) | Component::RootDir => return Err(WorkspacePathError::Prefix),
        }
    }
    Ok(resolved)
}

pub fn is_probably_binary(bytes: &[u8]) -> bool {
    bytes.iter().take(4096).any(|byte| *byte == 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_parent_traversal() {
        assert_eq!(
            safe_workspace_path(Path::new("/workspace"), Path::new("../secret")),
            Err(WorkspacePathError::ParentTraversal)
        );
    }

    #[test]
    fn accepts_nested_files() {
        assert_eq!(
            safe_workspace_path(Path::new("/workspace"), Path::new("src/main.rs")),
            Ok(PathBuf::from("/workspace/src/main.rs"))
        );
    }

    #[test]
    fn detects_binary_content() {
        assert!(is_probably_binary(&[1, 0, 2]));
        assert!(!is_probably_binary("مرحبا".as_bytes()));
    }
}
