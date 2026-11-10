//! Page geometry shared by the Word and PDF document engines.

/// A page's physical dimensions, stored in typographic points.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PageSize {
    /// Page width in points.
    pub width_pt: f32,
    /// Page height in points.
    pub height_pt: f32,
}

impl PageSize {
    /// Convert OOXML twips (1/20 point) into a validated page size.
    pub fn from_twips(width: u32, height: u32) -> Result<Self, GeometryError> {
        if width == 0 || height == 0 || width > 144_000 || height > 144_000 {
            return Err(GeometryError::InvalidPageSize);
        }
        Ok(Self { width_pt: width as f32 / 20.0, height_pt: height as f32 / 20.0 })
    }
}

/// Page margins, stored in typographic points.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Margins {
    /// Left margin in points.
    pub left_pt: f32,
    /// Top margin in points.
    pub top_pt: f32,
    /// Right margin in points.
    pub right_pt: f32,
    /// Bottom margin in points.
    pub bottom_pt: f32,
}

impl Margins {
    /// Convert OOXML twips into validated margins.
    pub fn from_twips(
        left: u32,
        top: u32,
        right: u32,
        bottom: u32,
    ) -> Result<Self, GeometryError> {
        if [left, top, right, bottom].iter().any(|value| *value > 144_000) {
            return Err(GeometryError::InvalidMargins);
        }
        Ok(Self {
            left_pt: left as f32 / 20.0,
            top_pt: top as f32 / 20.0,
            right_pt: right as f32 / 20.0,
            bottom_pt: bottom as f32 / 20.0,
        })
    }
}

/// A validated page size and its margins.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PageGeometry {
    /// Physical page size.
    pub page: PageSize,
    /// Page margins.
    pub margins: Margins,
}

impl PageGeometry {
    /// Create page geometry and reject margins that leave no printable area.
    pub fn new(page: PageSize, margins: Margins) -> Result<Self, GeometryError> {
        if margins.left_pt + margins.right_pt >= page.width_pt
            || margins.top_pt + margins.bottom_pt >= page.height_pt
        {
            return Err(GeometryError::MarginsExceedPage);
        }
        Ok(Self { page, margins })
    }

    /// Return printable width in points.
    pub fn content_width_pt(&self) -> f32 {
        self.page.width_pt - self.margins.left_pt - self.margins.right_pt
    }

    /// Return printable height in points.
    pub fn content_height_pt(&self) -> f32 {
        self.page.height_pt - self.margins.top_pt - self.margins.bottom_pt
    }
}

/// Errors returned while validating document page geometry.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum GeometryError {
    /// Page dimensions are zero or outside the supported safety bound.
    InvalidPageSize,
    /// One or more margins are outside the supported safety bound.
    InvalidMargins,
    /// Margins consume the entire page.
    MarginsExceedPage,
}
