# Advanced Skills Matching Engine for Resume Analysis
# Implements sophisticated skill extraction, categorization, and matching algorithms

import asyncio
import logging
import re
import time
from collections import Counter, defaultdict
from typing import Dict, List, Optional, Set, Tuple, Any
from dataclasses import dataclass, asdict
from enum import Enum
import json

from langchain_core.language_models import BaseLanguageModel
from langchain_core.output_parsers import JsonOutputParser
from langchain_core.prompts import ChatPromptTemplate

from ai_chain import create_llm, _execute_with_circuit_breaker
from ai_optimizer import get_ai_optimizer


class SkillCategory(Enum):
    """Categories for skill classification."""
    TECHNICAL_PROGRAMMING = "technical_programming"
    TECHNICAL_TOOLS = "technical_tools" 
    TECHNICAL_PLATFORMS = "technical_platforms"
    TECHNICAL_DATABASES = "technical_databases"
    SOFT_COMMUNICATION = "soft_communication"
    SOFT_LEADERSHIP = "soft_leadership"
    SOFT_ANALYTICAL = "soft_analytical"
    DOMAIN_SPECIFIC = "domain_specific"
    CERTIFICATIONS = "certifications"
    LANGUAGES = "languages"
    UNKNOWN = "unknown"


class SkillLevel(Enum):
    """Skill proficiency levels."""
    BEGINNER = "beginner"
    INTERMEDIATE = "intermediate" 
    ADVANCED = "advanced"
    EXPERT = "expert"
    UNKNOWN = "unknown"


class MatchStrength(Enum):
    """Strength of skill matching."""
    EXACT = "exact"          # Exact match
    STRONG = "strong"        # Synonyms, abbreviations
    MODERATE = "moderate"    # Related skills
    WEAK = "weak"           # Tangentially related
    NONE = "none"           # No match


@dataclass
class ExtractedSkill:
    """Represents a skill extracted from text."""
    skill_name: str
    category: SkillCategory
    level: SkillLevel
    context: str  # Context where skill was found
    confidence: float  # 0.0 to 1.0 confidence in extraction
    aliases: List[str]  # Alternative names for this skill
    evidence_score: float  # How well-supported the skill claim is


@dataclass
class SkillMatch:
    """Represents a match between resume skill and job requirement."""
    resume_skill: str
    job_requirement: str
    match_strength: MatchStrength
    similarity_score: float  # 0.0 to 1.0
    category: SkillCategory
    is_required: bool
    is_preferred: bool
    gap_analysis: str  # How to bridge the gap if not exact match


@dataclass
class SkillsAnalysisResult:
    """Complete skills analysis results."""
    extracted_skills: List[ExtractedSkill]
    job_requirements: List[ExtractedSkill]
    skill_matches: List[SkillMatch]
    missing_critical_skills: List[str]
    additional_skills: List[str]  # Skills in resume but not in job
    category_coverage: Dict[str, float]  # Coverage by category
    overall_match_score: float  # 0.0 to 1.0
    recommendations: List[str]
    skill_gap_priority: List[Dict[str, Any]]


class SkillsMatchingEngine:
    """Advanced skills matching engine with semantic understanding."""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.optimizer = get_ai_optimizer()
        
        # Load skill taxonomies and synonyms
        self.skill_taxonomies = self._load_skill_taxonomies()
        self.skill_synonyms = self._load_skill_synonyms()
        self.skill_patterns = self._compile_skill_patterns()
        
        # Weights for different matching strategies
        self.matching_weights = {
            "exact_match": 1.0,
            "synonym_match": 0.9,
            "semantic_similarity": 0.8,
            "category_match": 0.6,
            "pattern_match": 0.7
        }
    
    async def analyze_skills_comprehensive(
        self,
        resume_text: str,
        job_description: str,
        model_provider: str = "openai",
        model_name: Optional[str] = None,
        enable_ai_enhancement: bool = True
    ) -> SkillsAnalysisResult:
        """
        Comprehensive skills analysis using hybrid AI + rule-based approach.
        
        Args:
            resume_text: Resume content to analyze
            job_description: Job description to match against
            model_provider: AI provider for enhancement
            model_name: Specific model to use
            enable_ai_enhancement: Whether to use AI for skill enhancement
            
        Returns:
            SkillsAnalysisResult with detailed analysis
        """
        start_time = time.time()
        self.logger.info("Starting comprehensive skills analysis")
        
        try:
            # Phase 1: Rule-based skill extraction (fast, reliable)
            resume_skills = await self._extract_skills_rule_based(resume_text, "resume")
            job_skills = await self._extract_skills_rule_based(job_description, "job")
            
            # Phase 2: AI-enhanced skill extraction (comprehensive, contextual)
            if enable_ai_enhancement:
                ai_resume_skills = await self._extract_skills_ai_enhanced(
                    resume_text, model_provider, model_name
                )
                ai_job_skills = await self._extract_skills_ai_enhanced(
                    job_description, model_provider, model_name
                )
                
                # Merge and deduplicate skills
                resume_skills = self._merge_skill_extractions(resume_skills, ai_resume_skills)
                job_skills = self._merge_skill_extractions(job_skills, ai_job_skills)
            
            # Phase 3: Advanced skill matching
            skill_matches = await self._match_skills_advanced(resume_skills, job_skills)
            
            # Phase 4: Gap analysis and recommendations
            analysis_result = await self._perform_gap_analysis(
                resume_skills, job_skills, skill_matches
            )
            
            processing_time = time.time() - start_time
            self.logger.info(f"Skills analysis completed in {processing_time:.2f}s")
            
            return analysis_result
            
        except Exception as e:
            self.logger.error(f"Skills analysis failed: {e}")
            return self._create_fallback_analysis_result()
    
    async def _extract_skills_rule_based(
        self, text: str, source_type: str
    ) -> List[ExtractedSkill]:
        """Extract skills using rule-based patterns and taxonomies."""
        skills = []
        text_lower = text.lower()
        
        # Extract skills from each taxonomy
        for category, skill_list in self.skill_taxonomies.items():
            for skill_info in skill_list:
                skill_name = skill_info["name"]
                patterns = skill_info.get("patterns", [skill_name.lower()])
                
                for pattern in patterns:
                    matches = list(re.finditer(rf'\b{re.escape(pattern)}\b', text_lower))
                    
                    for match in matches:
                        # Extract context around the skill
                        start = max(0, match.start() - 50)
                        end = min(len(text), match.end() + 50)
                        context = text[start:end].strip()
                        
                        # Assess skill level from context
                        level = self._assess_skill_level_from_context(context, skill_name)
                        
                        # Calculate confidence based on context
                        confidence = self._calculate_extraction_confidence(context, skill_name, source_type)
                        
                        skills.append(ExtractedSkill(
                            skill_name=skill_name,
                            category=SkillCategory(category),
                            level=level,
                            context=context,
                            confidence=confidence,
                            aliases=skill_info.get("aliases", []),
                            evidence_score=self._calculate_evidence_score(context, skill_name)
                        ))
        
        # Deduplicate and merge similar skills
        return self._deduplicate_skills(skills)
    
    async def _extract_skills_ai_enhanced(
        self, text: str, model_provider: str, model_name: Optional[str]
    ) -> List[ExtractedSkill]:
        """Use AI to extract skills with semantic understanding."""
        try:
            llm = create_llm(model_provider, model_name, temperature=0.2)
            
            prompt = self._get_ai_skill_extraction_prompt()
            template = ChatPromptTemplate.from_template(prompt)
            chain = template | llm | JsonOutputParser()
            
            result = await _execute_with_circuit_breaker(
                chain, {"text_content": text}, model_name or "default", self.optimizer
            )
            
            ai_skills = []
            for skill_data in result.get("skills", []):
                try:
                    ai_skills.append(ExtractedSkill(
                        skill_name=skill_data.get("name", ""),
                        category=SkillCategory(skill_data.get("category", "unknown")),
                        level=SkillLevel(skill_data.get("level", "unknown")),
                        context=skill_data.get("context", ""),
                        confidence=skill_data.get("confidence", 0.5),
                        aliases=skill_data.get("aliases", []),
                        evidence_score=skill_data.get("evidence_score", 0.5)
                    ))
                except (ValueError, KeyError) as e:
                    self.logger.warning(f"Invalid AI skill extraction result: {e}")
                    continue
            
            return ai_skills
            
        except Exception as e:
            self.logger.error(f"AI skill extraction failed: {e}")
            return []
    
    async def _match_skills_advanced(
        self, resume_skills: List[ExtractedSkill], job_skills: List[ExtractedSkill]
    ) -> List[SkillMatch]:
        """Perform advanced skill matching with multiple strategies."""
        matches = []
        
        for job_skill in job_skills:
            best_match = None
            best_score = 0.0
            
            for resume_skill in resume_skills:
                # Strategy 1: Exact match
                exact_score = self._calculate_exact_match_score(resume_skill, job_skill)
                
                # Strategy 2: Synonym matching
                synonym_score = self._calculate_synonym_match_score(resume_skill, job_skill)
                
                # Strategy 3: Semantic similarity
                semantic_score = self._calculate_semantic_similarity(resume_skill, job_skill)
                
                # Strategy 4: Category match
                category_score = self._calculate_category_match_score(resume_skill, job_skill)
                
                # Weighted combined score
                combined_score = (
                    exact_score * self.matching_weights["exact_match"] +
                    synonym_score * self.matching_weights["synonym_match"] +
                    semantic_score * self.matching_weights["semantic_similarity"] +
                    category_score * self.matching_weights["category_match"]
                ) / sum(self.matching_weights.values())
                
                if combined_score > best_score:
                    best_score = combined_score
                    best_match = resume_skill
            
            # Determine match strength
            if best_score >= 0.9:
                match_strength = MatchStrength.EXACT
            elif best_score >= 0.7:
                match_strength = MatchStrength.STRONG
            elif best_score >= 0.5:
                match_strength = MatchStrength.MODERATE
            elif best_score >= 0.3:
                match_strength = MatchStrength.WEAK
            else:
                match_strength = MatchStrength.NONE
            
            matches.append(SkillMatch(
                resume_skill=best_match.skill_name if best_match else "",
                job_requirement=job_skill.skill_name,
                match_strength=match_strength,
                similarity_score=best_score,
                category=job_skill.category,
                is_required=self._is_required_skill(job_skill),
                is_preferred=self._is_preferred_skill(job_skill),
                gap_analysis=self._generate_gap_analysis(best_match, job_skill, best_score)
            ))
        
        return matches
    
    async def _perform_gap_analysis(
        self,
        resume_skills: List[ExtractedSkill],
        job_skills: List[ExtractedSkill],
        matches: List[SkillMatch]
    ) -> SkillsAnalysisResult:
        """Perform comprehensive gap analysis and generate recommendations."""
        
        # Identify missing critical skills
        missing_critical = []
        for match in matches:
            if match.is_required and match.match_strength == MatchStrength.NONE:
                missing_critical.append(match.job_requirement)
        
        # Identify additional skills (resume has but job doesn't require)
        job_skill_names = {skill.skill_name.lower() for skill in job_skills}
        additional_skills = []
        for skill in resume_skills:
            if skill.skill_name.lower() not in job_skill_names:
                additional_skills.append(skill.skill_name)
        
        # Calculate category coverage
        category_coverage = self._calculate_category_coverage(matches)
        
        # Calculate overall match score
        overall_score = self._calculate_overall_match_score(matches)
        
        # Generate recommendations
        recommendations = self._generate_recommendations(matches, missing_critical, category_coverage)
        
        # Prioritize skill gaps
        skill_gap_priority = self._prioritize_skill_gaps(matches, missing_critical)
        
        return SkillsAnalysisResult(
            extracted_skills=resume_skills,
            job_requirements=job_skills,
            skill_matches=matches,
            missing_critical_skills=missing_critical,
            additional_skills=additional_skills,
            category_coverage=category_coverage,
            overall_match_score=overall_score,
            recommendations=recommendations,
            skill_gap_priority=skill_gap_priority
        )
    
    def _load_skill_taxonomies(self) -> Dict[str, List[Dict[str, Any]]]:
        """Load comprehensive skill taxonomies."""
        return {
            "technical_programming": [
                {"name": "Python", "aliases": ["python3", "py"], "patterns": ["python", "python3"]},
                {"name": "JavaScript", "aliases": ["js", "node.js", "nodejs"], "patterns": ["javascript", "js", "node"]},
                {"name": "Java", "aliases": ["jvm"], "patterns": ["java", "jvm"]},
                {"name": "TypeScript", "aliases": ["ts"], "patterns": ["typescript", "ts"]},
                {"name": "React", "aliases": ["reactjs", "react.js"], "patterns": ["react", "reactjs"]},
                {"name": "Angular", "aliases": ["angularjs"], "patterns": ["angular", "angularjs"]},
                {"name": "Vue.js", "aliases": ["vue", "vuejs"], "patterns": ["vue", "vue.js", "vuejs"]},
                {"name": "C++", "aliases": ["cpp", "c plus plus"], "patterns": ["c\\+\\+", "cpp"]},
                {"name": "C#", "aliases": ["csharp", "c sharp"], "patterns": ["c#", "csharp"]},
                {"name": "Go", "aliases": ["golang"], "patterns": ["golang", "go"]},
                {"name": "Rust", "aliases": [], "patterns": ["rust"]},
                {"name": "Ruby", "aliases": ["ruby on rails", "rails"], "patterns": ["ruby", "rails"]},
                {"name": "PHP", "aliases": [], "patterns": ["php"]},
                {"name": "Swift", "aliases": ["ios swift"], "patterns": ["swift"]},
                {"name": "Kotlin", "aliases": [], "patterns": ["kotlin"]},
                {"name": "Scala", "aliases": [], "patterns": ["scala"]},
                {"name": "R", "aliases": ["r programming"], "patterns": [r"\br\b", "r programming"]},
            ],
            "technical_tools": [
                {"name": "Docker", "aliases": ["containerization"], "patterns": ["docker", "containerization"]},
                {"name": "Kubernetes", "aliases": ["k8s"], "patterns": ["kubernetes", "k8s"]},
                {"name": "Git", "aliases": ["version control"], "patterns": ["git", "github", "gitlab"]},
                {"name": "Jenkins", "aliases": ["ci/cd"], "patterns": ["jenkins"]},
                {"name": "Terraform", "aliases": ["infrastructure as code"], "patterns": ["terraform"]},
                {"name": "Ansible", "aliases": ["automation"], "patterns": ["ansible"]},
                {"name": "Maven", "aliases": [], "patterns": ["maven"]},
                {"name": "Gradle", "aliases": [], "patterns": ["gradle"]},
                {"name": "Webpack", "aliases": [], "patterns": ["webpack"]},
                {"name": "Jest", "aliases": ["testing framework"], "patterns": ["jest"]},
                {"name": "Postman", "aliases": ["api testing"], "patterns": ["postman"]},
            ],
            "technical_platforms": [
                {"name": "AWS", "aliases": ["amazon web services"], "patterns": ["aws", "amazon web services"]},
                {"name": "Azure", "aliases": ["microsoft azure"], "patterns": ["azure", "microsoft azure"]},
                {"name": "Google Cloud", "aliases": ["gcp", "google cloud platform"], "patterns": ["google cloud", "gcp"]},
                {"name": "Heroku", "aliases": [], "patterns": ["heroku"]},
                {"name": "Vercel", "aliases": [], "patterns": ["vercel"]},
                {"name": "Netlify", "aliases": [], "patterns": ["netlify"]},
            ],
            "technical_databases": [
                {"name": "PostgreSQL", "aliases": ["postgres"], "patterns": ["postgresql", "postgres"]},
                {"name": "MySQL", "aliases": [], "patterns": ["mysql"]},
                {"name": "MongoDB", "aliases": ["mongo"], "patterns": ["mongodb", "mongo"]},
                {"name": "Redis", "aliases": ["cache"], "patterns": ["redis"]},
                {"name": "Elasticsearch", "aliases": ["elastic search"], "patterns": ["elasticsearch"]},
                {"name": "SQLite", "aliases": [], "patterns": ["sqlite"]},
                {"name": "Oracle", "aliases": ["oracle db"], "patterns": ["oracle"]},
                {"name": "SQL Server", "aliases": ["microsoft sql"], "patterns": ["sql server", "mssql"]},
            ],
            "soft_communication": [
                {"name": "Communication", "aliases": ["verbal communication", "written communication"], "patterns": ["communication", "presentation"]},
                {"name": "Public Speaking", "aliases": ["presentations"], "patterns": ["public speaking", "presentations"]},
                {"name": "Technical Writing", "aliases": ["documentation"], "patterns": ["technical writing", "documentation"]},
                {"name": "Cross-functional Collaboration", "aliases": ["teamwork"], "patterns": ["collaboration", "teamwork"]},
            ],
            "soft_leadership": [
                {"name": "Leadership", "aliases": ["team leadership"], "patterns": ["leadership", "lead", "manage"]},
                {"name": "Project Management", "aliases": ["pm"], "patterns": ["project management", "project manager"]},
                {"name": "Mentoring", "aliases": ["coaching"], "patterns": ["mentoring", "coaching", "mentor"]},
                {"name": "Strategic Planning", "aliases": ["strategy"], "patterns": ["strategic planning", "strategy"]},
            ],
            "soft_analytical": [
                {"name": "Problem Solving", "aliases": ["analytical thinking"], "patterns": ["problem solving", "analytical"]},
                {"name": "Critical Thinking", "aliases": [], "patterns": ["critical thinking"]},
                {"name": "Data Analysis", "aliases": ["analytics"], "patterns": ["data analysis", "analytics"]},
                {"name": "Research", "aliases": [], "patterns": ["research", "investigation"]},
            ],
            "certifications": [
                {"name": "AWS Certified", "aliases": [], "patterns": ["aws certified", "aws certification"]},
                {"name": "PMP", "aliases": ["project management professional"], "patterns": ["pmp", "project management professional"]},
                {"name": "Scrum Master", "aliases": ["csm", "psm"], "patterns": ["scrum master", "csm", "psm"]},
                {"name": "Google Analytics", "aliases": [], "patterns": ["google analytics certified"]},
            ]
        }
    
    def _load_skill_synonyms(self) -> Dict[str, List[str]]:
        """Load skill synonyms for better matching."""
        return {
            "javascript": ["js", "node.js", "nodejs", "ecmascript"],
            "python": ["python3", "py"],
            "machine learning": ["ml", "ai", "artificial intelligence"],
            "database": ["db", "databases", "data storage"],
            "api": ["rest api", "restful", "web services"],
            "frontend": ["front-end", "ui", "user interface"],
            "backend": ["back-end", "server-side"],
            "devops": ["dev ops", "site reliability"],
            "agile": ["scrum", "kanban", "agile methodology"],
            "cloud": ["cloud computing", "cloud platforms"],
        }
    
    def _compile_skill_patterns(self) -> Dict[str, re.Pattern]:
        """Compile regex patterns for efficient skill matching."""
        patterns = {}
        for category, skills in self.skill_taxonomies.items():
            for skill in skills:
                for pattern in skill.get("patterns", []):
                    patterns[f"{skill['name']}_{pattern}"] = re.compile(
                        rf'\b{pattern}\b', re.IGNORECASE
                    )
        return patterns
    
    def _assess_skill_level_from_context(self, context: str, skill_name: str) -> SkillLevel:
        """Assess skill level based on context clues."""
        context_lower = context.lower()
        
        # Expert level indicators
        expert_indicators = ["expert", "architect", "lead", "senior", "advanced", "10+ years", "extensive experience"]
        if any(indicator in context_lower for indicator in expert_indicators):
            return SkillLevel.EXPERT
        
        # Advanced level indicators
        advanced_indicators = ["advanced", "proficient", "5+ years", "experienced", "deep knowledge"]
        if any(indicator in context_lower for indicator in advanced_indicators):
            return SkillLevel.ADVANCED
        
        # Intermediate level indicators
        intermediate_indicators = ["intermediate", "working knowledge", "2+ years", "familiar", "experience with"]
        if any(indicator in context_lower for indicator in intermediate_indicators):
            return SkillLevel.INTERMEDIATE
        
        # Beginner level indicators
        beginner_indicators = ["basic", "beginner", "learning", "introduction", "coursework", "academic"]
        if any(indicator in context_lower for indicator in beginner_indicators):
            return SkillLevel.BEGINNER
        
        return SkillLevel.UNKNOWN
    
    def _calculate_extraction_confidence(self, context: str, skill_name: str, source_type: str) -> float:
        """Calculate confidence in skill extraction."""
        base_confidence = 0.5
        
        # Higher confidence if skill appears in context with action verbs
        action_verbs = ["developed", "implemented", "created", "built", "designed", "managed", "led"]
        if any(verb in context.lower() for verb in action_verbs):
            base_confidence += 0.2
        
        # Higher confidence for specific mentions
        if skill_name.lower() in context.lower():
            base_confidence += 0.2
        
        # Job descriptions are more reliable for requirements
        if source_type == "job":
            base_confidence += 0.1
        
        # Resume experience sections are more reliable
        experience_indicators = ["experience", "worked", "project", "responsible"]
        if any(indicator in context.lower() for indicator in experience_indicators):
            base_confidence += 0.1
        
        return min(1.0, base_confidence)
    
    def _calculate_evidence_score(self, context: str, skill_name: str) -> float:
        """Calculate how well-evidenced a skill claim is."""
        evidence_score = 0.3
        
        # Quantified achievements
        if re.search(r'\d+', context):
            evidence_score += 0.2
        
        # Specific projects or outcomes
        outcome_indicators = ["resulted in", "achieved", "improved", "reduced", "increased"]
        if any(indicator in context.lower() for indicator in outcome_indicators):
            evidence_score += 0.3
        
        # Duration mentions
        duration_patterns = [r'\d+\s*years?', r'\d+\s*months?', r'since\s*\d{4}']
        if any(re.search(pattern, context.lower()) for pattern in duration_patterns):
            evidence_score += 0.2
        
        return min(1.0, evidence_score)
    
    def _deduplicate_skills(self, skills: List[ExtractedSkill]) -> List[ExtractedSkill]:
        """Remove duplicate skills and merge similar ones."""
        skill_map = {}
        
        for skill in skills:
            key = skill.skill_name.lower()
            if key in skill_map:
                # Keep the one with higher confidence
                if skill.confidence > skill_map[key].confidence:
                    skill_map[key] = skill
            else:
                skill_map[key] = skill
        
        return list(skill_map.values())
    
    def _merge_skill_extractions(
        self, rule_based: List[ExtractedSkill], ai_based: List[ExtractedSkill]
    ) -> List[ExtractedSkill]:
        """Merge rule-based and AI-based skill extractions."""
        merged_skills = {}
        
        # Add rule-based skills first (they're typically more reliable)
        for skill in rule_based:
            key = skill.skill_name.lower()
            merged_skills[key] = skill
        
        # Add AI-based skills, but prefer rule-based if there's a conflict
        for skill in ai_based:
            key = skill.skill_name.lower()
            if key not in merged_skills:
                merged_skills[key] = skill
            else:
                # Merge information, keeping the best parts
                existing = merged_skills[key]
                if skill.confidence > existing.confidence:
                    # Use AI skill but keep rule-based category if available
                    skill.category = existing.category if existing.category != SkillCategory.UNKNOWN else skill.category
                    merged_skills[key] = skill
        
        return list(merged_skills.values())
    
    def _calculate_exact_match_score(self, resume_skill: ExtractedSkill, job_skill: ExtractedSkill) -> float:
        """Calculate exact match score between skills."""
        if resume_skill.skill_name.lower() == job_skill.skill_name.lower():
            return 1.0
        
        # Check aliases
        for alias in resume_skill.aliases:
            if alias.lower() == job_skill.skill_name.lower():
                return 0.9
        
        for alias in job_skill.aliases:
            if alias.lower() == resume_skill.skill_name.lower():
                return 0.9
        
        return 0.0
    
    def _calculate_synonym_match_score(self, resume_skill: ExtractedSkill, job_skill: ExtractedSkill) -> float:
        """Calculate synonym-based match score."""
        skill1_lower = resume_skill.skill_name.lower()
        skill2_lower = job_skill.skill_name.lower()
        
        # Check if either skill is in synonyms of the other
        for skill, synonyms in self.skill_synonyms.items():
            if skill1_lower == skill and skill2_lower in [s.lower() for s in synonyms]:
                return 0.8
            if skill2_lower == skill and skill1_lower in [s.lower() for s in synonyms]:
                return 0.8
        
        return 0.0
    
    def _calculate_semantic_similarity(self, resume_skill: ExtractedSkill, job_skill: ExtractedSkill) -> float:
        """Calculate semantic similarity between skills."""
        # This is a simplified implementation
        # In production, you might use word embeddings or semantic similarity models
        
        skill1_words = set(resume_skill.skill_name.lower().split())
        skill2_words = set(job_skill.skill_name.lower().split())
        
        if not skill1_words or not skill2_words:
            return 0.0
        
        # Jaccard similarity
        intersection = skill1_words.intersection(skill2_words)
        union = skill1_words.union(skill2_words)
        
        return len(intersection) / len(union) if union else 0.0
    
    def _calculate_category_match_score(self, resume_skill: ExtractedSkill, job_skill: ExtractedSkill) -> float:
        """Calculate category-based match score."""
        if resume_skill.category == job_skill.category:
            return 0.3  # Lower score as category match alone isn't strong
        
        # Related categories get some score
        related_categories = {
            SkillCategory.TECHNICAL_PROGRAMMING: [SkillCategory.TECHNICAL_TOOLS],
            SkillCategory.SOFT_LEADERSHIP: [SkillCategory.SOFT_COMMUNICATION],
            SkillCategory.SOFT_ANALYTICAL: [SkillCategory.TECHNICAL_PROGRAMMING],
        }
        
        if job_skill.category in related_categories.get(resume_skill.category, []):
            return 0.1
        
        return 0.0
    
    def _is_required_skill(self, skill: ExtractedSkill) -> bool:
        """Determine if a skill is required based on context."""
        required_indicators = ["required", "must have", "essential", "mandatory"]
        return any(indicator in skill.context.lower() for indicator in required_indicators)
    
    def _is_preferred_skill(self, skill: ExtractedSkill) -> bool:
        """Determine if a skill is preferred based on context."""
        preferred_indicators = ["preferred", "nice to have", "bonus", "plus", "desirable"]
        return any(indicator in skill.context.lower() for indicator in preferred_indicators)
    
    def _generate_gap_analysis(self, resume_skill: Optional[ExtractedSkill], job_skill: ExtractedSkill, score: float) -> str:
        """Generate gap analysis for a skill mismatch."""
        if score >= 0.9:
            return "Strong match - no action needed"
        elif score >= 0.7:
            return "Good match - consider highlighting this skill more prominently"
        elif score >= 0.5:
            return "Partial match - consider gaining more experience or certification"
        elif resume_skill:
            return f"Weak match - consider training to bridge gap between {resume_skill.skill_name} and {job_skill.skill_name}"
        else:
            return f"Missing skill - consider learning {job_skill.skill_name}"
    
    def _calculate_category_coverage(self, matches: List[SkillMatch]) -> Dict[str, float]:
        """Calculate coverage percentage by skill category."""
        category_required = defaultdict(int)
        category_matched = defaultdict(int)
        
        for match in matches:
            category = match.category.value
            if match.is_required:
                category_required[category] += 1
                if match.match_strength in [MatchStrength.EXACT, MatchStrength.STRONG]:
                    category_matched[category] += 1
        
        coverage = {}
        for category in category_required:
            coverage[category] = category_matched[category] / category_required[category] if category_required[category] > 0 else 0.0
        
        return coverage
    
    def _calculate_overall_match_score(self, matches: List[SkillMatch]) -> float:
        """Calculate overall skills match score."""
        if not matches:
            return 0.0
        
        total_weight = 0
        weighted_score = 0
        
        for match in matches:
            # Weight required skills more heavily
            weight = 1.0 if match.is_required else 0.5
            
            # Convert match strength to score
            strength_scores = {
                MatchStrength.EXACT: 1.0,
                MatchStrength.STRONG: 0.8,
                MatchStrength.MODERATE: 0.6,
                MatchStrength.WEAK: 0.3,
                MatchStrength.NONE: 0.0
            }
            
            score = strength_scores[match.match_strength]
            weighted_score += score * weight
            total_weight += weight
        
        return weighted_score / total_weight if total_weight > 0 else 0.0
    
    def _generate_recommendations(
        self, matches: List[SkillMatch], missing_critical: List[str], category_coverage: Dict[str, float]
    ) -> List[str]:
        """Generate actionable recommendations."""
        recommendations = []
        
        # Critical missing skills
        if missing_critical:
            recommendations.append(f"Priority: Learn these critical missing skills: {', '.join(missing_critical[:3])}")
        
        # Low category coverage
        for category, coverage in category_coverage.items():
            if coverage < 0.5:
                recommendations.append(f"Improve {category.replace('_', ' ')} skills - only {coverage:.0%} coverage")
        
        # Weak matches that could be strengthened
        weak_matches = [m for m in matches if m.match_strength == MatchStrength.WEAK and m.is_required]
        if weak_matches:
            recommendations.append(f"Strengthen these skills through training or projects: {', '.join([m.job_requirement for m in weak_matches[:3]])}")
        
        # Skills to highlight better
        moderate_matches = [m for m in matches if m.match_strength == MatchStrength.MODERATE]
        if moderate_matches:
            recommendations.append("Consider highlighting these existing skills more prominently in your resume")
        
        return recommendations
    
    def _prioritize_skill_gaps(self, matches: List[SkillMatch], missing_critical: List[str]) -> List[Dict[str, Any]]:
        """Prioritize skill gaps by importance and difficulty."""
        gaps = []
        
        for match in matches:
            if match.match_strength == MatchStrength.NONE and match.is_required:
                # Estimate learning difficulty (simplified)
                difficulty = "Medium"  # This could be enhanced with more sophisticated analysis
                
                gaps.append({
                    "skill": match.job_requirement,
                    "priority": "High" if match.is_required else "Medium",
                    "difficulty": difficulty,
                    "category": match.category.value,
                    "recommendation": match.gap_analysis
                })
        
        # Sort by priority and difficulty
        priority_order = {"High": 3, "Medium": 2, "Low": 1}
        difficulty_order = {"Easy": 3, "Medium": 2, "Hard": 1}
        
        gaps.sort(key=lambda x: (priority_order[x["priority"]], difficulty_order[x["difficulty"]]), reverse=True)
        
        return gaps
    
    def _get_ai_skill_extraction_prompt(self) -> str:
        """Get prompt template for AI-enhanced skill extraction."""
        return """Extract skills from the following text. For each skill, provide detailed analysis.

Text to analyze:
{text_content}

Extract skills and return JSON with this structure:
{{"skills": [
    {{
        "name": "skill name",
        "category": "technical_programming|technical_tools|technical_platforms|technical_databases|soft_communication|soft_leadership|soft_analytical|domain_specific|certifications|languages|unknown",
        "level": "beginner|intermediate|advanced|expert|unknown",
        "context": "surrounding context where skill was found",
        "confidence": 0.0-1.0,
        "aliases": ["alternative names"],
        "evidence_score": 0.0-1.0
    }}
]}}

Focus on:
1. Technical skills (programming languages, tools, platforms)
2. Soft skills (communication, leadership, analytical)
3. Certifications and qualifications
4. Domain-specific expertise

Be conservative with confidence scores. Only assign high scores when there's clear evidence.

JSON:"""
    
    def _create_fallback_analysis_result(self) -> SkillsAnalysisResult:
        """Create fallback result when analysis fails."""
        return SkillsAnalysisResult(
            extracted_skills=[],
            job_requirements=[],
            skill_matches=[],
            missing_critical_skills=["Analysis unavailable"],
            additional_skills=[],
            category_coverage={},
            overall_match_score=0.0,
            recommendations=["Skills analysis failed. Manual review recommended."],
            skill_gap_priority=[]
        )


# Factory function
def create_skills_matching_engine() -> SkillsMatchingEngine:
    """Create and return a configured skills matching engine."""
    return SkillsMatchingEngine()


# Utility function for integration
async def analyze_skills_for_job(
    resume_text: str,
    job_description: str,
    model_provider: str = "openai",
    enable_ai_enhancement: bool = True
) -> Dict[str, Any]:
    """
    Analyze skills matching and return serializable results.
    
    Integrates with existing Brain service for resume analysis workflows.
    """
    engine = create_skills_matching_engine()
    
    try:
        result = await engine.analyze_skills_comprehensive(
            resume_text=resume_text,
            job_description=job_description,
            model_provider=model_provider,
            enable_ai_enhancement=enable_ai_enhancement
        )
        
        return {
            "status": "success",
            "analysis": asdict(result),
            "summary": {
                "overall_match_score": result.overall_match_score,
                "missing_critical_count": len(result.missing_critical_skills),
                "additional_skills_count": len(result.additional_skills),
                "top_recommendations": result.recommendations[:3]
            }
        }
        
    except Exception as e:
        logging.getLogger(__name__).error(f"Skills analysis failed: {e}")
        return {
            "status": "error",
            "error": str(e),
            "message": "Skills analysis failed. Please try again."
        }