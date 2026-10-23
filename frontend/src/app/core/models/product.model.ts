/**
 * Catalogue contracts, mirroring product-service's DTOs exactly
 * (`ProductSummaryResponse`, `ProductPageResponse`, `CategoryResponse`).
 */

/** One product in a listing grid. */
export interface ProductSummary {
  id: number;
  sku: string;
  name: string;
  slug: string;
  shortDescription: string;
  price: number;
  mrp: number;
  currency: string;
  categoryId: number;
  categoryName: string;
  brandId: number;
  brandName: string;
  imageUrl: string;
  active: boolean;
  averageRating: number;
  ratingCount: number;
  createdAt: string;
}

/** One page of a product listing (spec 5.3 pagination convention). */
export interface ProductPage {
  content: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** One category tree node (top-level categories have children). */
export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string;
  parentId: number | null;
  active: boolean;
  children: Category[];
}

/** Brand as needed for filters — mirrors product-service's BrandResponse. */
export interface Brand {
  id: number;
  name: string;
  slug: string;
}

/** A product image (product-service's ProductImageResponse). */
export interface ProductImage {
  id: number;
  url: string;
  altText: string;
  sortOrder: number;
  primary: boolean;
}

/** A spec line (product-service's ProductAttributeResponse). */
export interface ProductAttribute {
  key: string;
  value: string;
}

/** The full product — product-service's ProductResponse (plain JSON). */
export interface ProductDetail extends ProductSummary {
  description: string;
  images: ProductImage[];
  attributes: ProductAttribute[];
}

/** Public stock view — inventory-service's GET /api/v1/inventory/{id}. */
export interface InventoryView {
  productId: number;
  inStock: boolean;
  availableQuantity: number;
}

/** Parameters for the product listing (all optional except paging). */
export interface ProductQuery {
  page?: number;
  size?: number;
  /** Whitelisted `field,direction`, e.g. `price,asc`, `averageRating,desc`. */
  sort?: string;
  q?: string;
  category?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
}
